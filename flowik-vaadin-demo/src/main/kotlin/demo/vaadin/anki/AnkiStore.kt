package demo.vaadin.anki

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import flowik.core.Computed
import flowik.core.ObservableEntity
import flowik.core.ObservableList
import flowik.core.Store
import flowik.core.action
import flowik.core.computed
import flowik.core.observable
import flowik.core.observableRef
import flowik.core.observables
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** The page the app is showing — what the view switches its content on. */
sealed interface Screen {
    data object Decks : Screen
    data class Overview(val deck: ObservableEntity<Deck>) : Screen
    data class Study(val deck: ObservableEntity<Deck>) : Screen

    /** The card browser, for one deck or — with `null` — for all of them. */
    data class Browse(val deck: ObservableEntity<Deck>?) : Screen
}

/**
 * What is left to study in a deck today, already in study order and cut to
 * the deck's daily limits.
 *
 * @param learning      learning and relearning cards due within the learn-ahead
 *                      limit, earliest first
 * @param reviews       review cards due today, the most overdue first
 * @param newCards      new cards still allowed today, in the order they were added
 * @param newHeldBack   new cards beyond today's limit
 * @param laterLearning learning cards due later today than the learn-ahead limit
 * @param dueTomorrow   review cards that fall due tomorrow
 */
class DeckQueue(
    val learning: List<ObservableEntity<Card>>,
    val reviews: List<ObservableEntity<Card>>,
    val newCards: List<ObservableEntity<Card>>,
    val newHeldBack: Int,
    val laterLearning: Int,
    val dueTomorrow: Int,
    val total: Int,
    /**
     * The card to show next: a learning card that is due, then reviews, then
     * new cards, and only when nothing else is left a learning card ahead of time.
     */
    val next: ObservableEntity<Card>?
) {
    val newCount: Int get() = newCards.size
    val learnCount: Int get() = learning.size
    val reviewCount: Int get() = reviews.size
    val isFinished: Boolean get() = next == null

    fun count(queue: Queue): Int = when (queue) {
        Queue.NEW -> newCount
        Queue.LEARN -> learnCount
        Queue.REVIEW -> reviewCount
    }

    companion object {
        val EMPTY = DeckQueue(emptyList(), emptyList(), emptyList(), 0, 0, 0, 0, null)
    }
}

/**
 * Every piece of application state, as observables, in memory: the decks, the
 * cards, the review log, and where the user is.
 *
 * The queues are *derived*: nothing keeps a list of due cards up to date.
 * [queues] is computed from the cards' schedules, the review log and the clock,
 * so answering a card, undoing it, editing a deck's limits or moving to the
 * next day all re-count every deck on screen with no extra wiring.
 *
 * Time only moves when [tick] reads the clock, which every navigation and
 * answer does, so a derivation never sees the time change halfway. [skipDay]
 * shifts that clock, which is how the demo shows reviews falling due.
 *
 * One per browser tab ([UIScope]), so decks survive a trip to another page.
 */
@SpringComponent
@UIScope
class AnkiStore(
    private val clock: Clock = Clock.systemDefaultZone(),
    val scheduler: Scheduler = Scheduler()
) : Store {

    val zone: ZoneId = clock.zone

    val decks = observables<Deck>()
    val cards = observables<Card>()

    /** Every answer, oldest first — today's limits are counted from it, and undo pops it. */
    val log = ObservableList<ReviewEntry>()

    var screen by observableRef<Screen>(Screen.Decks, name = "screen")
    var answerShown by observable(false, name = "answerShown")

    /** How far the demo has moved the clock ahead of real time. */
    var timeShift by observable(Duration.ZERO, name = "timeShift")
    var now by observable(clock.instant(), name = "now")

    /** A card brought back by undo, shown before anything else in its deck. */
    private var undone by observableRef<ObservableEntity<Card>?>(null, name = "undone")

    private var lastId = 0L

    val today: LocalDate by computed { localDate(now, zone) }

    /** Every deck's queue, keyed by deck id. */
    val queues: Computed<Map<Long, DeckQueue>> = computed {
        val today = today
        val doneToday = log.items.filter { localDate(it.time, zone) == today }
        val cardsByDeck = cards.items.groupBy { it.deckId }
        decks.items.associate { deck ->
            deck.id to buildQueue(deck, cardsByDeck[deck.id].orEmpty(), doneToday.filter { it.card.deckId == deck.id })
        }
    }

    /** The card being studied, or `null` outside the study screen and once the deck is done. */
    val currentCard: ObservableEntity<Card>? by computed {
        val study = screen as? Screen.Study ?: return@computed null
        undone?.takeIf { it.deckId == study.deck.id } ?: queueOf(study.deck).next
    }

    /** The intervals shown above the answer buttons for the current card. */
    val answerLabels: Map<Answer, String> by computed {
        val card = currentCard ?: return@computed emptyMap()
        scheduler.preview(card[Card::schedule], now, zone).mapValues { scheduler.intervalLabel(it.value, now) }
    }

    val studiedToday by computed { log.items.count { localDate(it.time, zone) == today } }
    val canUndo by computed { log.size > 0 }
    val daysShifted by computed { timeShift.toDays().toInt() }

    init {
        seedSampleDecks(this)
    }

    fun queueOf(deck: ObservableEntity<Deck>): DeckQueue = queues.value[deck.id] ?: DeckQueue.EMPTY

    fun cardsOf(deck: ObservableEntity<Deck>): List<ObservableEntity<Card>> =
        cards.items.filter { it.deckId == deck.id }

    fun deckOf(card: ObservableEntity<Card>): ObservableEntity<Deck>? = decks.items.find { it.id == card.deckId }

    private fun buildQueue(
        deck: ObservableEntity<Deck>,
        deckCards: List<ObservableEntity<Card>>,
        doneToday: List<ReviewEntry>
    ): DeckQueue {
        val learnAheadUntil = now + scheduler.options.learnAhead
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()

        val newLimit = (deck[Deck::newPerDay] - doneToday.count { it.before.state == CardState.NEW }).coerceAtLeast(0)
        val reviewLimit = (deck[Deck::reviewsPerDay] - doneToday.count { it.before.state == CardState.REVIEW })
            .coerceAtLeast(0)

        val byState = deckCards.groupBy { it[Card::schedule].state }
        val learning = (byState[CardState.LEARNING].orEmpty() + byState[CardState.RELEARNING].orEmpty())
            .sortedBy { it[Card::schedule].due }
        val dueLearning = learning.filter { it[Card::schedule].due <= learnAheadUntil }
        val laterLearning = learning.count { it[Card::schedule].due in learnAheadUntil..endOfToday }

        val allReviews = byState[CardState.REVIEW].orEmpty()
        val dueTomorrow = allReviews.count { localDate(it[Card::schedule].due, zone) == today.plusDays(1) }
        val reviews = allReviews
            .filter { localDate(it[Card::schedule].due, zone) <= today }
            .sortedBy { it[Card::schedule].due }
            .take(reviewLimit)

        val allNew = byState[CardState.NEW].orEmpty().sortedBy { it.id }
        val newCards = allNew.take(newLimit)

        val next = dueLearning.firstOrNull { it[Card::schedule].due <= now }
            ?: reviews.firstOrNull()
            ?: newCards.firstOrNull()
            ?: dueLearning.firstOrNull()

        return DeckQueue(
            dueLearning, reviews, newCards,
            newHeldBack = allNew.size - newCards.size,
            laterLearning = laterLearning,
            dueTomorrow = dueTomorrow,
            total = deckCards.size,
            next = next
        )
    }

    // Time

    /** Reads the clock — the only way [now] changes. */
    fun tick() = action {
        now = clock.instant() + timeShift
    }

    /** Moves the demo one day into the future, so cards scheduled for tomorrow fall due. */
    fun skipDay() = action {
        timeShift += Duration.ofDays(1)
        tick()
    }

    // Navigation

    fun showDecks() = navigate(Screen.Decks)
    fun showOverview(deck: ObservableEntity<Deck>) = navigate(Screen.Overview(deck))
    fun study(deck: ObservableEntity<Deck>) = navigate(Screen.Study(deck))
    fun browse(deck: ObservableEntity<Deck>?) = navigate(Screen.Browse(deck))

    private fun navigate(target: Screen) = action {
        tick()
        screen = target
        answerShown = false
        undone = null
    }

    // Studying

    fun showAnswer() = action {
        if (currentCard != null) answerShown = true
    }

    fun answer(answer: Answer) = action {
        val card = currentCard ?: return@action
        if (!answerShown) return@action

        val before = card[Card::schedule]
        card[Card::schedule] = scheduler.answer(before, answer, now, zone)
        log.add(ReviewEntry(card, now, answer, before))
        answerShown = false
        undone = null
        tick()
    }

    /** Takes the last answer back and shows that card again, as Anki's Undo does. */
    fun undo() = action {
        val entry = log.items.lastOrNull() ?: return@action
        log.removeAt(log.size - 1)
        entry.card[Card::schedule] = entry.before
        tick()
        deckOf(entry.card)?.let { screen = Screen.Study(it) }
        answerShown = false
        undone = entry.card
    }

    // Decks

    fun isDeckNameTaken(name: String, except: ObservableEntity<Deck>? = null) =
        decks.items.any { it !== except && it[Deck::name].equals(name.trim(), ignoreCase = true) }

    /** Creates a deck, or returns `null` when the name is empty or taken. */
    fun createDeck(name: String, description: String = ""): ObservableEntity<Deck>? = action {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isDeckNameTaken(trimmed)) return@action null
        decks.add(Deck(nextId(), trimmed, description))
    }

    fun deleteDeck(deck: ObservableEntity<Deck>) = action {
        cards.removeAll { it.deckId == deck.id }
        log.removeAll { it.card.deckId == deck.id }
        decks.remove(deck)
        if (screen != Screen.Decks && screen != Screen.Browse(null)) screen = Screen.Decks
    }

    // Cards

    /** Adds a card at the end of [deck]'s new queue, or returns `null` when either side is blank. */
    fun addCard(
        deck: ObservableEntity<Deck>,
        front: String,
        back: String,
        schedule: Schedule = Schedule()
    ): ObservableEntity<Card>? = action {
        if (front.isBlank() || back.isBlank()) return@action null
        cards.add(Card(nextId(), deck.id, front.trim(), back.trim(), schedule))
    }

    fun deleteCard(card: ObservableEntity<Card>) = action {
        log.removeAll { it.card === card }
        cards.remove(card)
        if (undone === card) undone = null
    }

    /** Anki's "Forget": the card starts over as a new card. */
    fun forgetCard(card: ObservableEntity<Card>) = action {
        card[Card::schedule] = Schedule()
    }

    private fun nextId() = ++lastId
}

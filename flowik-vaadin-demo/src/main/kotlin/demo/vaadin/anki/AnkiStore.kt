package demo.vaadin.anki

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import demo.vaadin.anki.backend.CardService
import demo.vaadin.anki.backend.DeckService
import demo.vaadin.anki.backend.ReviewService
import flowik.core.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The page the app is showing — what the view switches its content on. */
sealed interface Screen {
    data object Decks : Screen
    data class Overview(val deck: ObservableEntity<Deck>) : Screen

    /**
     * Studying [deck]. [resume] is the card to show first — the one an undo
     * just brought back — rather than whatever the queue would pick.
     *
     * Not a data class: each one is a new study session, which the view
     * builds afresh even when it shows the same deck and card — as when the
     * same answer is undone twice in a row.
     */
    class Study(val deck: ObservableEntity<Deck>, val resume: ObservableEntity<Card>? = null) : Screen {
        override fun toString() = "Study(deck=$deck, resume=$resume)"
    }

    /** The card browser, for one deck or — with `null` — for all of them. */
    data class Browse(val deck: ObservableEntity<Deck>?) : Screen
}

/**
 * The application's state, as observables, in memory: the decks, the cards
 * loaded so far, today's reviews, the clock, and which screen is showing.
 *
 * The store is the UI's copy of the data, not where it is kept, and it holds
 * only what the screens need. It starts empty; [refresh] loads the decks —
 * each with its counts for today, worked out by the backend — and today's
 * reviews, which is all the deck list needs and all the daily limits count.
 * The view calls it when the page is shown and on the way back to the deck
 * list, so changes saved from other tabs show up there. A deck's *cards* are
 * loaded when something needs them: opening, studying or browsing the deck,
 * adding a card to it or changing its limits. Every action that changes the
 * data saves through the backend first and updates the observables once that
 * returns, so a failed save leaves the screen as it was.
 *
 * It holds what more than one screen depends on, and nothing else. The state
 * of a study session — which card is up, whether its answer is showing — is
 * read by the study screen alone, so it lives there; the store's actions take
 * the card they act on as an argument instead.
 *
 * The queues are *derived*: nothing keeps a list of due cards up to date.
 * [queueOf] is computed per loaded deck from that deck's cards, its answers
 * today, its limits and the clock — by [buildQueue], the same rules the
 * backend counts with — so answering a card, undoing it, editing a deck's
 * limits or moving to the next day re-count the decks on screen with no extra
 * wiring, and only the decks the change concerns. [countsOf] shows the
 * backend's counts for a deck until its cards are loaded, and the derived
 * ones from then on.
 *
 * Time only moves when [tick] reads the clock, which every navigation and
 * answer does, so a derivation never sees the time change halfway. [skipDay]
 * shifts that clock, which is how the demo shows reviews falling due.
 *
 * One per browser tab ([UIScope]), so decks survive a trip to another page;
 * the data itself is the backend's, shared by every tab.
 */
@SpringComponent
@UIScope
class AnkiStore(
    private val deckService: DeckService,
    private val cardService: CardService,
    private val reviewService: ReviewService,
    private val clock: Clock = Clock.systemDefaultZone(),
    val scheduler: Scheduler = Scheduler()
) : Store {

    val zone: ZoneId = clock.zone

    val decks = observables<Deck>()

    /** The cards of the decks loaded so far — all of a deck's cards or none. */
    val cards = observables<Card>()

    /** Today's answers in every deck, oldest first — the daily limits are counted from it, and undo pops it. */
    val log = ObservableList<Review>()

    /** The backend's counts of the decks whose cards are not loaded; a loaded deck counts itself. */
    private val backendCounts = ObservableMap<Long, DeckCounts>()

    /** The decks whose cards are in [cards]. Read only by actions, so it need not be observable. */
    private val loadedDecks = mutableSetOf<Long>()

    var screen by observableRef<Screen>(Screen.Decks, name = "screen")

    /** How far the demo has moved the clock ahead of real time. */
    var timeShift by observable(Duration.ZERO, name = "timeShift")
    var now by observable(clock.instant(), name = "now")

    /** Today's date — it notifies when the day changes, not on every tick of [now]. */
    val today: LocalDate by computedStruct { localDate(now, zone) }

    /**
     * The clock to the minute, which is what the queues compare learning cards
     * against: answering a card ticks [now], and that should not recount every
     * deck as long as the minute stays the same.
     */
    private val minute by computedStruct { now.truncatedTo(ChronoUnit.MINUTES) }

    /** A deck's cards — notifies only when cards join or leave that deck, not when one is answered. */
    private val deckCards = computedFn(equals = Comparer.Structural) { deckId: Long ->
        cards.items.filter { it.deckId == deckId }
    }

    /** What a deck already studied today, for its limits — notifies only when its own counts change. */
    private val doneToday = computedFn(equals = Comparer.Structural) { deckId: Long ->
        val today = today
        DoneToday.of(log.items.filter { it.deckId == deckId && localDate(it.time, zone) == today })
    }

    /**
     * Each deck's queue, cached while shown. It reads only that deck's inputs,
     * so answering a card recomputes the queue of the card's deck and no other.
     */
    private val deckQueue = computedFn { deck: ObservableEntity<Deck> ->
        buildQueue(
            deckCards(deck.id), { it[Card::schedule] },
            deck[Deck::newPerDay], deck[Deck::reviewsPerDay], doneToday(deck.id),
            minute, zone, scheduler.options.learnAhead
        )
    }

    val studiedToday by computed { log.items.count { localDate(it.time, zone) == today } }
    val canUndo by computed { log.size > 0 }
    val daysShifted by computed { timeShift.toDays().toInt() }

    /** Today's queue of a deck whose cards are loaded — the screens that show one load it first. */
    fun queueOf(deck: ObservableEntity<Deck>): DeckQueue = deckQueue(deck)

    /**
     * A deck's counts for the deck list: the backend's until its cards are
     * loaded, then derived from them like [queueOf], so they follow every answer.
     */
    fun countsOf(deck: ObservableEntity<Deck>): DeckCounts = backendCounts[deck.id] ?: queueOf(deck).counts

    fun cardsOf(deck: ObservableEntity<Deck>): List<ObservableEntity<Card>> = deckCards(deck.id)

    fun deckOf(card: ObservableEntity<Card>): ObservableEntity<Deck>? = decks.items.find { it.id == card.deckId }

    /**
     * When [card] would come back for each answer — the labels above the answer
     * buttons. Reads the card's schedule and the clock, so a binding showing it
     * follows both.
     */
    fun intervalLabels(card: ObservableEntity<Card>): Map<Answer, String> =
        scheduler.preview(card[Card::schedule], now, zone).mapValues { scheduler.intervalLabel(it.value, now) }

    // Loading

    /**
     * Replaces the decks, their counts and today's reviews with what the
     * backend has now, other tabs' changes included, and drops the loaded
     * cards. The entities are new ones, so the screen is pointed at the new
     * entities by id, and the cards it shows are loaded again; a screen whose
     * deck is gone falls back to the deck list.
     */
    fun refresh() = action {
        tick()
        val summaries = deckService.findAll(now, zone)
        decks.setAll(summaries.map { it.deck })
        backendCounts.setAll(summaries.associate { it.deck.id to it.counts })
        cards.clear()
        loadedDecks.clear()
        log.setAll(reviewService.findSince(startOfToday()))

        val decksById = decks.items.associateBy { it.id }
        screen = when (val shown = screen) {
            Screen.Decks -> shown
            is Screen.Overview -> decksById[shown.deck.id]
                ?.let { loadCards(it); Screen.Overview(it) }
                ?: Screen.Decks
            is Screen.Study -> decksById[shown.deck.id]
                ?.let { loadCards(it); Screen.Study(it, resume = shown.resume?.let { card -> cardById(card.id) }) }
                ?: Screen.Decks
            is Screen.Browse -> shown.deck?.let { decksById[it.id] }
                .also { deck -> if (deck != null) loadCards(deck) else decks.items.forEach(::loadCards) }
                .let { Screen.Browse(it) }
        }
    }

    /**
     * Loads [deck]'s cards unless they already are. From then on the deck is
     * counted from its cards instead of by the backend. Called from actions.
     */
    private fun loadCards(deck: ObservableEntity<Deck>) {
        if (deck.id in loadedDecks) return
        cards.addAll(cardService.findByDeck(deck.id))
        loadedDecks += deck.id
        backendCounts.remove(deck.id)
    }

    private fun cardById(id: Long) = cards.items.find { it.id == id }

    private fun startOfToday(): Instant = today.atStartOfDay(zone).toInstant()

    // Time

    /** Reads the clock — the only way [now] changes. */
    fun tick() = action {
        now = clock.instant() + timeShift
    }

    /**
     * Moves the demo one day into the future, so cards scheduled for tomorrow
     * fall due. The loaded decks recount themselves; the others are counted
     * again by the backend.
     */
    fun skipDay() = action {
        timeShift += Duration.ofDays(1)
        tick()
        val counted = decks.items.map { it.id }.filter { it !in loadedDecks }.toSet()
        backendCounts.setAll(
            deckService.findAll(now, zone).filter { it.deck.id in counted }.associate { it.deck.id to it.counts }
        )
    }

    // Navigation

    /** Back to the deck list, reloading the data on the way — the screen to see other tabs' changes on. */
    fun showDecks() = action {
        screen = Screen.Decks
        refresh()
    }

    fun showOverview(deck: ObservableEntity<Deck>) = action {
        loadCards(deck)
        navigate(Screen.Overview(deck))
    }

    fun study(deck: ObservableEntity<Deck>) = action {
        loadCards(deck)
        navigate(Screen.Study(deck))
    }

    /** The card browser for [deck], or for every deck — which loads the cards of all of them. */
    fun browse(deck: ObservableEntity<Deck>?) = action {
        if (deck != null) loadCards(deck) else decks.items.forEach(::loadCards)
        navigate(Screen.Browse(deck))
    }

    private fun navigate(target: Screen) = action {
        tick()
        screen = target
    }

    // Studying

    /** Schedules [card] according to [answer], and logs it so it counts towards today and can be undone. */
    fun answer(card: ObservableEntity<Card>, answer: Answer) = action {
        val schedule = scheduler.answer(card[Card::schedule], answer, now, zone)
        val review = reviewService.record(card.id, answer, now, schedule)
        card[Card::schedule] = schedule
        log.add(review)
        tick()
    }

    /**
     * Takes the last answer back and studies that card again, as Anki's Undo
     * does — in its own deck, which need not be the one on screen, nor even
     * loaded: today's reviews may come from before the page was opened.
     */
    fun undo() = action {
        val review = log.items.lastOrNull() ?: return@action
        reviewService.undo(review.id)
        log.removeAt(log.size - 1)
        tick()
        val deck = decks.items.find { it.id == review.deckId } ?: return@action
        loadCards(deck)
        val card = cardById(review.cardId) ?: return@action
        card[Card::schedule] = review.before
        screen = Screen.Study(deck, resume = card)
    }

    // Decks

    fun isDeckNameTaken(name: String, except: ObservableEntity<Deck>? = null) =
        decks.items.any { it !== except && it[Deck::name].equals(name.trim(), ignoreCase = true) }

    /** Creates a deck, or returns `null` when the name is empty or taken. */
    fun createDeck(name: String, description: String = ""): ObservableEntity<Deck>? = action {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || isDeckNameTaken(trimmed)) return@action null
        val deck = deckService.create(trimmed, description)
        loadedDecks += deck.id // a new deck has no cards to load, so it counts itself from the start
        decks.add(deck)
    }

    fun deleteDeck(deck: ObservableEntity<Deck>) = action {
        deckService.delete(deck.id)
        cards.removeAll { it.deckId == deck.id }
        log.removeAll { it.deckId == deck.id }
        backendCounts.remove(deck.id)
        loadedDecks -= deck.id
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
        loadCards(deck) // first, so the new card is not loaded a second time, and the deck counts it
        cards.add(cardService.create(deck.id, front.trim(), back.trim(), schedule))
    }

    fun deleteCard(card: ObservableEntity<Card>) = action {
        cardService.delete(card.id)
        log.removeAll { it.cardId == card.id }
        cards.remove(card)
    }

    /** Replaces both sides of [card]; blank sides are ignored. */
    fun updateCard(card: ObservableEntity<Card>, front: String, back: String) = action {
        if (front.isBlank() || back.isBlank()) return@action
        val saved = card.current().copy(front = front.trim(), back = back.trim())
        cardService.save(saved)
        card[Card::front] = saved.front
        card[Card::back] = saved.back
    }

    /**
     * Applies the editable settings in [changes] — name, description and daily
     * limits — to [deck]. A name that is empty or taken is left as it was.
     * Loads the deck's cards, so new limits recount it.
     */
    fun updateDeck(deck: ObservableEntity<Deck>, changes: Deck) = action {
        loadCards(deck)
        val name = changes.name.trim()
        val saved = Deck(
            id = deck.id,
            name = if (name.isNotEmpty() && !isDeckNameTaken(name, except = deck)) name else deck[Deck::name],
            description = changes.description,
            newPerDay = changes.newPerDay.coerceAtLeast(0),
            reviewsPerDay = changes.reviewsPerDay.coerceAtLeast(0)
        )
        deckService.save(saved)
        deck[Deck::name] = saved.name
        deck[Deck::description] = saved.description
        deck[Deck::newPerDay] = saved.newPerDay
        deck[Deck::reviewsPerDay] = saved.reviewsPerDay
    }

    /** Anki's "Forget": the card starts over as a new card. */
    fun forgetCard(card: ObservableEntity<Card>) = action {
        cardService.save(card.current().copy(schedule = Schedule()))
        card[Card::schedule] = Schedule()
    }

    /** The card as it stands now — [ObservableEntity.snapshot] is only what it was created with. */
    private fun ObservableEntity<Card>.current() =
        Card(id, deckId, this[Card::front], this[Card::back], this[Card::schedule])
}

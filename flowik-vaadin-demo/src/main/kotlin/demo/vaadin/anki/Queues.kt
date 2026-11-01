package demo.vaadin.anki

import flowik.core.ObservableEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A deck's three counts for today, and how many cards it holds. */
data class DeckCounts(val newCount: Int, val learnCount: Int, val reviewCount: Int, val total: Int) {

    fun count(queue: Queue): Int = when (queue) {
        Queue.NEW -> newCount
        Queue.LEARN -> learnCount
        Queue.REVIEW -> reviewCount
    }
}

/** How many new and review cards of a deck were answered today — what its daily limits subtract. */
data class DoneToday(val newCards: Int, val reviews: Int) {

    companion object {
        /** Counts today's [reviews] of one deck by what the card was before the answer. */
        fun of(reviews: List<Review>) = DoneToday(
            newCards = reviews.count { it.before.state == CardState.NEW },
            reviews = reviews.count { it.before.state == CardState.REVIEW }
        )
    }
}

/**
 * What is left to study in a deck today, already in study order and cut to
 * the deck's daily limits. [C] is whatever holds a card: the store's
 * observable entities, or the backend's plain [Card]s when it counts decks.
 *
 * @param learning      learning and relearning cards due within the learn-ahead
 *                      limit, earliest first
 * @param reviews       review cards due today, the most overdue first
 * @param newCards      new cards still allowed today, in the order they were added
 * @param newHeldBack   new cards beyond today's limit
 * @param laterLearning learning cards due later today than the learn-ahead limit
 * @param dueTomorrow   review cards that fall due tomorrow
 */
class CardQueue<C>(
    val learning: List<C>,
    val reviews: List<C>,
    val newCards: List<C>,
    val newHeldBack: Int,
    val laterLearning: Int,
    val dueTomorrow: Int,
    val total: Int,
    /**
     * The card to show next: a learning card that is due, then reviews, then
     * new cards, and only when nothing else is left a learning card ahead of time.
     */
    val next: C?
) {
    val newCount: Int get() = newCards.size
    val learnCount: Int get() = learning.size
    val reviewCount: Int get() = reviews.size
    val isFinished: Boolean get() = next == null

    val counts: DeckCounts get() = DeckCounts(newCount, learnCount, reviewCount, total)

    fun count(queue: Queue): Int = counts.count(queue)
}

/** A deck's queue as the screens use it, over the store's card entities. */
typealias DeckQueue = CardQueue<ObservableEntity<Card>>

/**
 * Builds a deck's queue for today from its cards — in the order they were
 * added — what it already studied today, its limits and the time. The one set
 * of rules, used by the store for the decks it has loaded and by the backend
 * to count the ones it has not.
 */
fun <C> buildQueue(
    cards: List<C>,
    scheduleOf: (C) -> Schedule,
    newPerDay: Int,
    reviewsPerDay: Int,
    doneToday: DoneToday,
    now: Instant,
    zone: ZoneId,
    learnAhead: Duration
): CardQueue<C> {
    val today = localDate(now, zone)
    val learnAheadUntil = now + learnAhead
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()

    val newLimit = (newPerDay - doneToday.newCards).coerceAtLeast(0)
    val reviewLimit = (reviewsPerDay - doneToday.reviews).coerceAtLeast(0)

    val byState = cards.groupBy { scheduleOf(it).state }
    val learning = (byState[CardState.LEARNING].orEmpty() + byState[CardState.RELEARNING].orEmpty())
        .sortedBy { scheduleOf(it).due }
    val dueLearning = learning.filter { scheduleOf(it).due <= learnAheadUntil }
    val laterLearning = learning.count { scheduleOf(it).due in learnAheadUntil..endOfToday }

    val allReviews = byState[CardState.REVIEW].orEmpty()
    val dueTomorrow = allReviews.count { localDate(scheduleOf(it).due, zone) == today.plusDays(1) }
    val reviews = allReviews
        .filter { localDate(scheduleOf(it).due, zone) <= today }
        .sortedBy { scheduleOf(it).due }
        .take(reviewLimit)

    val allNew = byState[CardState.NEW].orEmpty()
    val newCards = allNew.take(newLimit)

    val next = dueLearning.firstOrNull { scheduleOf(it).due <= now }
        ?: reviews.firstOrNull()
        ?: newCards.firstOrNull()
        ?: dueLearning.firstOrNull()

    return CardQueue(
        dueLearning, reviews, newCards,
        newHeldBack = allNew.size - newCards.size,
        laterLearning = laterLearning,
        dueTomorrow = dueTomorrow,
        total = cards.size,
        next = next
    )
}

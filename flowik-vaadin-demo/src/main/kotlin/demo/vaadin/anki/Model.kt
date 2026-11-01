package demo.vaadin.anki

import flowik.core.ObservableEntity
import java.time.Instant

/**
 * A collection of cards studied together, with its own daily limits.
 *
 * @param newPerDay     how many new cards may be introduced per day
 * @param reviewsPerDay how many review cards may be shown per day
 */
data class Deck(
    val id: Long,
    val name: String,
    val description: String = "",
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200
)

/**
 * One flashcard. Its text is edited through the card's atoms, and its place in
 * the scheduler lives in [schedule] — kept as a single atomic value because
 * the scheduler always replaces it as a whole.
 */
data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val schedule: Schedule = Schedule()
)

/** Where a card is in its life, as in Anki's card info. */
enum class CardState(val title: String) {
    NEW("New"),
    LEARNING("Learning"),
    REVIEW("Review"),
    RELEARNING("Relearning");

    /** The queue the card is counted in on the deck screens. */
    val queue: Queue
        get() = when (this) {
            NEW -> Queue.NEW
            LEARNING, RELEARNING -> Queue.LEARN
            REVIEW -> Queue.REVIEW
        }
}

/** The three counts shown for every deck. */
enum class Queue { NEW, LEARN, REVIEW }

/**
 * A card's scheduling state — everything the SM-2 algorithm needs.
 *
 * @param due          when the card is next shown; for review cards it is the
 *                     start of the due day, since reviews are day-based
 * @param step         index into the learning or relearning steps
 * @param intervalDays the current review interval; while relearning, the
 *                     interval the card goes back to once it graduates again
 * @param ease         the interval multiplier, 2.5 = 250%; 0 until the card is
 *                     first answered
 */
data class Schedule(
    val state: CardState = CardState.NEW,
    val due: Instant = Instant.EPOCH,
    val step: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0
)

/** The four answer buttons, with the keys that press them. */
enum class Answer(val title: String, val key: Int) {
    AGAIN("Again", 1),
    HARD("Hard", 2),
    GOOD("Good", 3),
    EASY("Easy", 4)
}

/**
 * One answered card, kept so the day's limits can be counted and the answer
 * undone — [before] is what the card's schedule goes back to. Plain ids, not
 * entities: today's reviews are loaded whether or not their decks' cards are.
 */
data class Review(
    val id: Long,
    val cardId: Long,
    val deckId: Long,
    val time: Instant,
    val answer: Answer,
    val before: Schedule
)

@get:JvmName("deckId")
val ObservableEntity<Deck>.id: Long get() = snapshot.id

@get:JvmName("cardId")
val ObservableEntity<Card>.id: Long get() = snapshot.id

@get:JvmName("cardDeckId")
val ObservableEntity<Card>.deckId: Long get() = snapshot.deckId

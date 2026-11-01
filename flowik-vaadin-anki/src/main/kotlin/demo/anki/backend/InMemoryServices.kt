package demo.anki.backend

import demo.anki.Answer
import demo.anki.Card
import demo.anki.Deck
import demo.anki.DoneToday
import demo.anki.Review
import demo.anki.Schedule
import demo.anki.SchedulerOptions
import demo.anki.buildQueue
import demo.anki.localDate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Counts each deck with [buildQueue], the rules the store uses for the decks it
 * has loaded — a real backend would do it in a query.
 */
@Service
class InMemoryDeckService(
    private val db: InMemoryAnkiDatabase,
    private val options: SchedulerOptions = SchedulerOptions()
) : DeckService {

    override fun findAll(now: Instant, zone: ZoneId) = db.transaction {
        val today = localDate(now, zone)
        val cardsByDeck = cards.values.groupBy { it.deckId }
        val reviewsByDeck = reviews.values.filter { localDate(it.time, zone) == today }.groupBy { it.deckId }
        decks.values.map { deck ->
            val queue = buildQueue(
                cardsByDeck[deck.id].orEmpty(), Card::schedule,
                deck.newPerDay, deck.reviewsPerDay, DoneToday.of(reviewsByDeck[deck.id].orEmpty()),
                now.truncatedTo(ChronoUnit.MINUTES), zone, options.learnAhead
            )
            DeckSummary(deck, queue.counts)
        }
    }

    override fun create(name: String, description: String) = db.transaction { insertDeck(name, description) }

    override fun save(deck: Deck) = db.transaction {
        require(deck.id in decks) { "No deck ${deck.id}" }
        decks[deck.id] = deck
    }

    override fun delete(deckId: Long) = db.transaction {
        cards.values.filter { it.deckId == deckId }.forEach { deleteCard(it.id) }
        decks.remove(deckId)
        Unit
    }
}

@Service
class InMemoryCardService(private val db: InMemoryAnkiDatabase) : CardService {

    override fun findByDeck(deckId: Long) = db.transaction { cards.values.filter { it.deckId == deckId } }

    override fun create(deckId: Long, front: String, back: String, schedule: Schedule) =
        db.transaction { insertCard(deckId, front, back, schedule) }

    override fun save(card: Card) = db.transaction {
        require(card.id in cards) { "No card ${card.id}" }
        cards[card.id] = card
    }

    override fun delete(cardId: Long) = db.transaction { deleteCard(cardId) }
}

@Service
class InMemoryReviewService(private val db: InMemoryAnkiDatabase) : ReviewService {

    override fun findSince(time: Instant) = db.transaction { reviews.values.filter { it.time >= time } }

    override fun record(cardId: Long, answer: Answer, time: Instant, schedule: Schedule) = db.transaction {
        val card = cards[cardId] ?: throw IllegalArgumentException("No card $cardId")
        cards[cardId] = card.copy(schedule = schedule)
        Review(nextId(), cardId, card.deckId, time, answer, card.schedule).also { reviews[it.id] = it }
    }

    override fun undo(reviewId: Long) {
        db.transaction {
            val review = reviews.remove(reviewId) ?: return@transaction
            cards[review.cardId]?.let { cards[it.id] = it.copy(schedule = review.before) }
        }
    }
}

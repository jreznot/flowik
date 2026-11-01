package demo.vaadin.anki.backend

import demo.vaadin.anki.Card
import demo.vaadin.anki.Deck
import demo.vaadin.anki.Review
import demo.vaadin.anki.Schedule
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * The tables the in-memory services share, standing in for a database.
 *
 * One for the whole application, so what a tab saves is there when the page is
 * reloaded or opened in another tab — though a tab already open does not see
 * it until it reloads. Calls come from many sessions, so the services do all
 * their work in a [transaction]: one call is one transaction, cascades included.
 *
 * Starts with the sample decks, scheduled relative to [clock].
 */
@Component
class InMemoryAnkiDatabase(clock: Clock = Clock.systemDefaultZone()) {

    private val lock = Any()

    val decks = LinkedHashMap<Long, Deck>()
    val cards = LinkedHashMap<Long, Card>()
    val reviews = LinkedHashMap<Long, Review>()

    private var lastId = 0L

    init {
        seedSampleDecks(this, clock)
    }

    fun nextId() = ++lastId

    fun <R> transaction(block: InMemoryAnkiDatabase.() -> R): R = synchronized(lock) { block() }

    fun insertDeck(name: String, description: String): Deck =
        Deck(nextId(), name, description).also { decks[it.id] = it }

    fun insertCard(deckId: Long, front: String, back: String, schedule: Schedule): Card {
        require(deckId in decks) { "No deck $deckId" }
        return Card(nextId(), deckId, front, back, schedule).also { cards[it.id] = it }
    }

    fun deleteCard(cardId: Long) {
        reviews.values.removeIf { it.cardId == cardId }
        cards.remove(cardId)
    }
}

package demo.anki.backend

import demo.anki.Card
import demo.anki.Schedule

/** Stores the cards. Blank sides are the caller's to reject. */
interface CardService {

    /** One deck's cards, in the order they were added. */
    fun findByDeck(deckId: Long): List<Card>

    fun create(deckId: Long, front: String, back: String, schedule: Schedule): Card

    fun save(card: Card)

    /** Deletes the card together with its reviews. */
    fun delete(cardId: Long)
}

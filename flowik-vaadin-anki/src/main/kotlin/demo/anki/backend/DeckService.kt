package demo.anki.backend

import demo.anki.Deck
import demo.anki.DeckCounts
import java.time.Instant
import java.time.ZoneId

/** A deck with its counts for today, so the deck list can show it without loading its cards. */
data class DeckSummary(val deck: Deck, val counts: DeckCounts)

/**
 * Stores the decks. A plain blocking interface over plain values: every call
 * returns once the change is stored, and the backend assigns ids. Validation —
 * empty or taken names — is the caller's job.
 */
interface DeckService {

    /**
     * Every deck with its New / Learn / Due counts at [now] — the caller's
     * clock, which the demo can move ahead — and its days in [zone].
     */
    fun findAll(now: Instant, zone: ZoneId): List<DeckSummary>

    fun create(name: String, description: String): Deck

    fun save(deck: Deck)

    /** Deletes the deck together with its cards and their reviews. */
    fun delete(deckId: Long)
}

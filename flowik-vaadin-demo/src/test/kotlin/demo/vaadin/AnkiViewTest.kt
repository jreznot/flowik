package demo.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import demo.vaadin.anki.*
import demo.vaadin.anki.backend.InMemoryAnkiDatabase
import demo.vaadin.anki.backend.InMemoryCardService
import demo.vaadin.anki.backend.InMemoryDeckService
import demo.vaadin.anki.backend.InMemoryReviewService
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.core.autoRun
import flowik.vaadin.testing.descendants
import flowik.vaadin.testing.find
import flowik.vaadin.testing.findByClass
import flowik.vaadin.testing.hasClass
import flowik.vaadin.testing.isShown
import flowik.vaadin.testing.setValueFromClient
import flowik.vaadin.testing.simulateClick
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnkiViewTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)

    private val db = InMemoryAnkiDatabase(clock)

    private fun store(clock: Clock = this.clock) =
        AnkiStore(InMemoryDeckService(db), InMemoryCardService(db), InMemoryReviewService(db), clock)

    private fun Component.shown(className: String) = findByClass(className).filter { it.isShown() }

    private fun AnkiStore.deck(name: String): ObservableEntity<Deck> = decks.items.single { it[Deck::name] == name }

    /** New / Learn / Due of each deck row, as the user reads them. */
    private fun AnkiView.deckCounts(): Map<String, List<String>> =
        descendants().filterIsInstance<DeckRow>().associate { row ->
            row.descendants().single { it.hasClass("anki-link") }.element.text to
                    row.descendants().filter { it.hasClass("anki-count") }.map { it.element.text }
        }

    private fun AnkiView.cardFront() = shown("anki-card-front").single().element.text

    private fun AnkiView.answerButton(answer: Answer) =
        shown("anki-answer-${answer.name.lowercase()}").single()

    private fun AnkiView.showAnswer() = shown("anki-show-answer").single().simulateClick()

    /** Shows the answer and rates the card, as the user does. */
    private fun AnkiView.answer(answer: Answer) {
        showAnswer()
        answerButton(answer).simulateClick()
    }

    private fun AnkiView.intervalLabels() =
        Answer.entries.associateWith { answerButton(it).descendants().first().element.text }

    private fun AnkiView.undo() = find<Button>().single { it.text == "Undo" }.simulateClick()

    private fun AnkiStore.card(front: String) = cards.items.single { it[Card::front] == front }

    @Test
    fun `the deck list counts what is due today within the limits`() {
        val view = AnkiView(store())

        assertEquals(
            mapOf(
                "Capitals of Europe" to listOf("20", "0", "0"),
                "Spanish Basics" to listOf("6", "1", "5"),
                "Flowik API" to listOf("12", "0", "0")
            ),
            view.deckCounts()
        )
    }

    @Test
    fun `studying shows learning cards first, then the most overdue reviews`() {
        val store = store()
        val view = AnkiView(store)
        store.study(store.deck("Spanish Basics"))

        assertEquals("cat", view.cardFront())
        assertTrue(view.shown("anki-card-back").isEmpty())
        assertTrue(view.shown("anki-answers").isEmpty())

        view.shown("anki-show-answer").single().simulateClick()
        assertEquals("el gato", view.shown("anki-card-back").single().element.text)
        assertEquals("1d", view.answerButton(Answer.GOOD).descendants().first().element.text)

        view.answerButton(Answer.GOOD).simulateClick()
        assertEquals("hello", view.cardFront())
        assertEquals(listOf("6", "0", "5"), view.shown("anki-study-counts").single().descendants().map { it.element.text })

        view.showAnswer()
        assertEquals(
            mapOf(Answer.AGAIN to "10m", Answer.HARD to "5d", Answer.GOOD to "12d", Answer.EASY to "22d"),
            view.intervalLabels()
        )
    }

    @Test
    fun `answering a card recounts only its own deck`() {
        val store = store()
        val view = AnkiView(store)
        val capitals = store.deck("Capitals of Europe")
        val spanish = store.deck("Spanish Basics")
        store.study(spanish)
        var capitalsRuns = 0
        var spanishRuns = 0
        autoRun { capitalsRuns++; store.countsOf(capitals) }
        autoRun { spanishRuns++; store.countsOf(spanish) }

        view.answer(Answer.GOOD)

        assertEquals(1, capitalsRuns)
        assertEquals(2, spanishRuns)
    }

    @Test
    fun `undo puts the card back and shows it again`() {
        val store = store()
        val view = AnkiView(store)
        store.study(store.deck("Spanish Basics"))
        val cat = store.card("cat")
        val before = cat[Card::schedule]

        view.answer(Answer.AGAIN)
        view.answer(Answer.GOOD)
        assertEquals(2, store.studiedToday)
        assertEquals("thank you", view.cardFront())

        view.undo()
        assertEquals(1, store.studiedToday)
        assertEquals("hello", view.cardFront())

        view.undo()
        assertEquals("cat", view.cardFront())
        assertEquals(before, cat[Card::schedule])
        assertTrue(view.shown("anki-card-back").isEmpty(), "an undone card starts from its question")
        assertFalse(store.canUndo)
    }

    @Test
    fun `undo goes back to the deck the card came from`() {
        val store = store()
        val view = AnkiView(store)
        val spanish = store.deck("Spanish Basics")
        store.study(spanish)
        view.answer(Answer.GOOD)

        store.study(store.deck("Flowik API"))
        assertEquals("Create a mutable atom", view.cardFront())

        view.undo()
        val study = store.screen as Screen.Study
        assertTrue(study.deck === spanish && study.resume === store.card("cat"))
        assertEquals("cat", view.cardFront())
    }

    @Test
    fun `the new card limit ends the day with a congratulations screen`() {
        val store = store()
        val view = AnkiView(store)
        val deck = store.deck("Flowik API")
        action { deck[Deck::newPerDay] = 2 }
        store.study(deck)

        repeat(2) { view.answer(Answer.EASY) }

        assertTrue(view.shown("anki-card-front").isEmpty())
        val congrats = view.shown("anki-congrats").single()
        assertTrue(congrats.descendants().any { it.element.text.startsWith("10 new cards held back") && it.isShown() })
        assertTrue(congrats.descendants().none { it.element.text.contains("due tomorrow") && it.isShown() })
    }

    @Test
    fun `learning cards are shown ahead of time once nothing else is left`() {
        val store = store()
        val view = AnkiView(store)
        val deck = store.deck("Flowik API")
        action { deck[Deck::newPerDay] = 1 }
        store.study(deck)
        val card = store.card(view.cardFront())

        view.answer(Answer.GOOD)

        // Due in ten minutes, but within the learn-ahead limit and the only card left.
        assertEquals(CardState.LEARNING, card[Card::schedule].state)
        assertEquals(card[Card::front], view.cardFront())
        assertTrue(view.shown("anki-card-back").isEmpty(), "the same card comes back with its question")
        assertEquals(1, store.queueOf(deck).learnCount)
    }

    @Test
    fun `skipping a day brings tomorrow's reviews due`() {
        val store = store()
        val view = AnkiView(store)
        val spanish = store.deck("Spanish Basics")
        val flowik = store.deck("Flowik API")
        store.showOverview(flowik)
        store.showOverview(spanish)
        assertEquals(2, store.queueOf(spanish).dueTomorrow)

        // Spanish counts itself from its loaded cards.
        view.descendants().single { it.hasClass("anki-next-day") }.simulateClick()
        assertEquals(1, store.daysShifted)
        assertEquals(DeckCounts(6, 1, 7, 15), store.countsOf(spanish))

        // Back on the deck list nothing is loaded, and the backend counts for the shifted day.
        store.showDecks()
        assertEquals(listOf("6", "1", "7"), view.deckCounts()["Spanish Basics"])
        view.descendants().single { it.hasClass("anki-next-day") }.simulateClick()
        assertEquals(listOf("6", "1", "8"), view.deckCounts()["Spanish Basics"])
    }

    @Test
    fun `navigation swaps the screen and the browser filters as you type`() {
        val store = store()
        val view = AnkiView(store)

        view.descendants().filterIsInstance<DeckRow>().first().descendants().single { it.hasClass("anki-link") }.simulateClick()
        assertNotNull(view.descendants().filterIsInstance<DeckOverview>().singleOrNull())

        store.browse(store.deck("Capitals of Europe"))
        val browser = view.find<CardBrowser>().single()
        val grid = browser.find<Grid<ObservableEntity<Card>>>().single()
        assertEquals(22, grid.listDataView.itemCount)
        assertTrue(view.find<DeckOverview>().isEmpty())

        browser.find<TextField>().single().setValueFromClient("lisbon")
        assertEquals(listOf("Lisbon"), grid.listDataView.items.toList().map { it[Card::back] })
    }

    @Test
    fun `the browser keeps its rows in step with the cards`() {
        val store = store()
        val view = AnkiView(store)
        store.browse(store.deck("Spanish Basics"))
        val grid = view.find<Grid<ObservableEntity<Card>>>().single()
        val deckFilter = view.find<Select<ObservableEntity<Deck>>>().single()
        assertEquals("Spanish Basics", deckFilter.value[Deck::name])

        val hello = grid.listDataView.items.toList().first { it[Card::front] == "hello" }
        store.deleteCard(hello)
        assertEquals(14, grid.listDataView.itemCount)

        // A deck created while browsing shows up in the filter, and the selection stays.
        store.createDeck("German")
        assertEquals(4, deckFilter.listDataView.itemCount)
        assertEquals("Spanish Basics", deckFilter.value[Deck::name])
    }

    @Test
    fun `deleting a deck removes its cards and leaves its screens`() {
        val store = store()
        val view = AnkiView(store)
        val capitals = store.deck("Capitals of Europe")
        store.showOverview(capitals)

        store.deleteDeck(capitals)

        assertEquals(Screen.Decks, store.screen)
        assertEquals(2, view.descendants().filterIsInstance<DeckRow>().size)
        assertTrue(store.cards.isEmpty(), "its cards were the only ones loaded")
    }

    @Test
    fun `cards are loaded only for the decks that need them`() {
        val store = store()
        val view = AnkiView(store)
        assertTrue(store.cards.isEmpty(), "the deck list shows the backend's counts")
        assertEquals(DeckCounts(12, 0, 0, 12), store.countsOf(store.deck("Flowik API")))

        store.showOverview(store.deck("Capitals of Europe"))
        assertEquals(setOf("Capitals of Europe"), store.cards.items.mapNotNull { store.deckOf(it)?.get(Deck::name) }.toSet())

        // Adding a card loads its deck, which then counts it.
        store.showDecks()
        store.addCard(store.deck("Flowik API"), "Dispose a group of bindings", "dispose()")
        assertEquals(13, store.cards.size)
        assertEquals(listOf("13", "0", "0"), view.deckCounts()["Flowik API"])

        // So does lowering a deck's limit.
        store.updateDeck(store.deck("Spanish Basics"), Deck(0, "Spanish Basics", newPerDay = 2, reviewsPerDay = 3))
        assertEquals(listOf("2", "1", "3"), view.deckCounts()["Spanish Basics"])

        store.browse(null)
        assertEquals(50, store.cards.size)
    }

    @Test
    fun `a new store loads what the last one saved`() {
        val first = store()
        val view = AnkiView(first)
        first.study(first.deck("Spanish Basics"))
        view.answer(Answer.GOOD)
        first.updateCard(first.card("dog"), "the dog", "el perro")
        first.deleteDeck(first.deck("Capitals of Europe"))
        first.createDeck("German")?.let { first.addCard(it, "house", "das Haus") }

        // As if the page were opened in a new tab: a fresh store over the same backend.
        val second = store()
        assertTrue(second.decks.isEmpty(), "nothing is loaded until the page is shown")
        second.refresh()
        assertEquals(listOf("Spanish Basics", "Flowik API", "German"), second.decks.items.map { it[Deck::name] })
        assertEquals(listOf("6", "0", "5"), AnkiView(second).deckCounts()["Spanish Basics"])
        assertEquals(1, second.studiedToday)
        assertTrue(second.cards.isEmpty())

        // Undo loads the deck of the answer it takes back.
        second.undo()
        val study = second.screen as Screen.Study
        assertTrue(study.deck === second.deck("Spanish Basics") && study.resume === second.card("cat"))
        assertEquals("el perro", second.card("the dog")[Card::back])
        val third = store().apply { refresh(); browse(null) }
        assertEquals(CardState.RELEARNING, third.card("cat")[Card::schedule].state)
    }

    @Test
    fun `a new store loads only today's reviews`() {
        val first = store()
        val view = AnkiView(first)
        first.study(first.deck("Spanish Basics"))
        view.answer(Answer.GOOD)

        val tomorrow = store(Clock.offset(clock, Duration.ofDays(1))).apply { refresh() }
        assertEquals(0, tomorrow.studiedToday)
        assertFalse(tomorrow.canUndo)
        assertEquals(1, store().apply { refresh() }.studiedToday)
    }

    @Test
    fun `going back to the deck list picks up other tabs' changes`() {
        val mine = store()
        val view = AnkiView(mine)
        val theirs = store().apply { refresh(); browse(null) }
        mine.showOverview(mine.deck("Spanish Basics"))

        theirs.createDeck("German")
        theirs.updateCard(theirs.card("cat"), "the cat", "el gato")
        assertEquals(3, mine.decks.size, "not until the deck list is shown again")

        mine.showDecks()
        assertEquals(4, view.descendants().filterIsInstance<DeckRow>().size)
        mine.browse(mine.deck("Spanish Basics"))
        assertEquals("el gato", mine.card("the cat")[Card::back])
    }

    @Test
    fun `refreshing keeps the screen on the same deck and card`() {
        val store = store()
        val view = AnkiView(store)
        store.study(store.deck("Spanish Basics"))
        view.answer(Answer.GOOD)
        view.undo()

        store.refresh()

        val study = store.screen as Screen.Study
        assertTrue(study.deck === store.deck("Spanish Basics"), "points at the reloaded deck")
        assertTrue(study.resume === store.card("cat"), "and the reloaded card")
        assertEquals("cat", view.cardFront())

        // The same card undone again: a new session that deals it again.
        view.answer(Answer.GOOD)
        view.undo()
        assertEquals("cat", view.cardFront())
    }
}

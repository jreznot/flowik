package demo.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import demo.vaadin.anki.*
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.vaadin.testing.descendants
import flowik.vaadin.testing.find
import flowik.vaadin.testing.findByClass
import flowik.vaadin.testing.hasClass
import flowik.vaadin.testing.isShown
import flowik.vaadin.testing.setValueFromClient
import flowik.vaadin.testing.simulateClick
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnkiViewTest {

    private val clock = Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC)

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

    @Test
    fun `the deck list counts what is due today within the limits`() {
        val view = AnkiView(AnkiStore(clock))

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
        val store = AnkiStore(clock)
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

        store.showAnswer()
        assertEquals(
            mapOf(Answer.AGAIN to "10m", Answer.HARD to "5d", Answer.GOOD to "12d", Answer.EASY to "22d"),
            store.answerLabels
        )
    }

    @Test
    fun `undo puts the card back and shows it again`() {
        val store = AnkiStore(clock)
        val view = AnkiView(store)
        store.study(store.deck("Spanish Basics"))
        val cat = store.currentCard!!
        val before = cat[Card::schedule]

        store.showAnswer()
        store.answer(Answer.AGAIN)
        store.showAnswer()
        store.answer(Answer.GOOD)
        assertEquals(2, store.studiedToday)

        store.undo()
        assertEquals(1, store.studiedToday)
        store.undo()
        assertSame(cat, store.currentCard)
        assertEquals(before, cat[Card::schedule])
        assertEquals("cat", view.cardFront())
        assertFalse(store.canUndo)
    }

    @Test
    fun `the new card limit ends the day with a congratulations screen`() {
        val store = AnkiStore(clock)
        val view = AnkiView(store)
        val deck = store.deck("Flowik API")
        action { deck[Deck::newPerDay] = 2 }
        store.study(deck)

        repeat(2) {
            store.showAnswer()
            store.answer(Answer.EASY)
        }

        assertNull(store.currentCard)
        val congrats = view.shown("anki-congrats").single()
        assertTrue(congrats.descendants().any { it.element.text.startsWith("10 new cards held back") && it.isShown() })
        assertTrue(congrats.descendants().none { it.element.text.contains("due tomorrow") && it.isShown() })
    }

    @Test
    fun `learning cards are shown ahead of time once nothing else is left`() {
        val store = AnkiStore(clock)
        val deck = store.deck("Flowik API")
        action { deck[Deck::newPerDay] = 1 }
        store.study(deck)
        val card = store.currentCard!!

        store.showAnswer()
        store.answer(Answer.GOOD)

        // Due in ten minutes, but within the learn-ahead limit and the only card left.
        assertEquals(CardState.LEARNING, card[Card::schedule].state)
        assertSame(card, store.currentCard)
        assertEquals(1, store.queueOf(deck).learnCount)
    }

    @Test
    fun `skipping a day brings tomorrow's reviews due`() {
        val store = AnkiStore(clock)
        val view = AnkiView(store)
        val spanish = store.deck("Spanish Basics")
        assertEquals(2, store.queueOf(spanish).dueTomorrow)

        view.descendants().single { it.hasClass("anki-next-day") }.simulateClick()

        assertEquals(1, store.daysShifted)
        assertEquals(listOf("6", "1", "7"), view.deckCounts()["Spanish Basics"])
    }

    @Test
    fun `navigation swaps the screen and the browser filters as you type`() {
        val store = AnkiStore(clock)
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
        val store = AnkiStore(clock)
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
        val store = AnkiStore(clock)
        val view = AnkiView(store)
        val capitals = store.deck("Capitals of Europe")
        store.showOverview(capitals)

        store.deleteDeck(capitals)

        assertEquals(Screen.Decks, store.screen)
        assertEquals(2, view.descendants().filterIsInstance<DeckRow>().size)
        assertEquals(27, store.cards.size)
    }
}

package demo.anki

import com.vaadin.flow.component.Component
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.core.observable
import flowik.core.observableRef
import flowik.vaadin.testing.descendants
import flowik.vaadin.testing.findByClass
import flowik.vaadin.testing.isShown
import flowik.vaadin.testing.simulateClick
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screens are stateless, so they are built here from plain observables —
 * no store, no scheduler — and checked for the two halves of the contract:
 * they show the state they are given, and they report events without acting
 * on them.
 */
class AnkiComponentsTest {

    private val deck = observable(Deck(1, "Spanish"))
    private val cat = observable(Card(2, 1, "cat", "el gato"))

    private fun queue(vararg newCards: ObservableEntity<Card>) = DeckQueue(
        learning = emptyList(), reviews = emptyList(), newCards = newCards.toList(),
        newHeldBack = 0, laterLearning = 0, dueTomorrow = 3, total = 10, next = newCards.firstOrNull()
    )

    private fun shown(root: Component, className: String) =
        root.findByClass(className).filter { it.isShown() }

    @Test
    fun `the study screen deals from its queue and reports each answer`() {
        val dog = observable(Card(3, 1, "dog", "el perro"))
        val current = observableRef(queue(cat, dog))
        val answers = mutableListOf<Pair<String, Answer>>()
        val screen = StudyScreen(
            deck = deck,
            queue = current,
            resume = null,
            intervalLabels = { mapOf(Answer.GOOD to "10m") },
            canUndo = { false },
            onAnswer = { card, answer -> answers += card[Card::front] to answer },
            onUndo = {},
            onEdit = {},
            onForget = {},
            onDelete = {},
            onBack = {},
            onDecks = {}
        )
        assertEquals("cat", shown(screen, "anki-card-front").single().element.text)
        assertTrue(shown(screen, "anki-card-back").isEmpty())

        // Showing the answer is the screen's own business: nothing to report.
        shown(screen, "anki-show-answer").single().simulateClick()
        assertEquals("el gato", shown(screen, "anki-card-back").single().element.text)
        val good = shown(screen, "anki-answer-good").single()
        assertEquals("10m", good.descendants().first().element.text)

        good.simulateClick()
        assertEquals(listOf("cat" to Answer.GOOD), answers)
        // Rescheduling is the owner's: until the queue moves on, the same card
        // is dealt again — from its question.
        assertEquals("cat", shown(screen, "anki-card-front").single().element.text)
        assertTrue(shown(screen, "anki-card-back").isEmpty())

        action { current.value = queue(dog) }
        assertEquals("dog", shown(screen, "anki-card-front").single().element.text)

        action { current.value = queue() }
        assertTrue(shown(screen, "anki-congrats").isNotEmpty())
        assertTrue(shown(screen, "anki-show-answer").isEmpty())
    }

    @Test
    fun `a resumed card is dealt before the queue's pick until it is answered`() {
        val dog = observable(Card(3, 1, "dog", "el perro"))
        val answers = mutableListOf<String>()
        val screen = StudyScreen(
            deck = deck,
            queue = { queue(cat) },
            resume = dog,
            intervalLabels = { emptyMap() },
            canUndo = { true },
            onAnswer = { card, _ -> answers += card[Card::front] },
            onUndo = {},
            onEdit = {},
            onForget = {},
            onDelete = {},
            onBack = {},
            onDecks = {}
        )
        assertEquals("dog", shown(screen, "anki-card-front").single().element.text)

        shown(screen, "anki-show-answer").single().simulateClick()
        shown(screen, "anki-answer-again").single().simulateClick()

        assertEquals(listOf("dog"), answers)
        assertEquals("cat", shown(screen, "anki-card-front").single().element.text)
    }

    @Test
    fun `the overview switches to the finished message when its queue runs out`() {
        val current = observableRef(queue(cat))
        var studied = 0
        val overview = DeckOverview(
            deck = deck,
            queue = current,
            onStudy = { studied++ },
            onBack = {},
            onAddCards = {},
            onBrowse = {},
            onOptions = {}
        )

        assertEquals(listOf("1", "0", "0"), shown(overview, "anki-count").map { it.element.text })
        shown(overview, "anki-study-now").single().simulateClick()
        assertEquals(1, studied)

        action { current.value = queue() }
        assertTrue(shown(overview, "anki-due").isEmpty())
        val finished = shown(overview, "anki-congrats").single()
        assertTrue(finished.descendants().any { it.isShown() && it.element.text.startsWith("3 reviews due tomorrow") })
    }
}

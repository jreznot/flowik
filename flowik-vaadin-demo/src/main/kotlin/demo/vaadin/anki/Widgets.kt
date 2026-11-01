package demo.vaadin.anki

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.shared.Tooltip
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.className
import flowik.vaadin.onClick
import flowik.vaadin.text
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * The dialogs the screens can ask for. The view owns them, so a screen only
 * says *what* it wants edited — and a test can stand in for the view.
 */
interface AnkiDialogs {
    fun addCards(deck: ObservableEntity<Deck>?)
    fun editCard(card: ObservableEntity<Card>)
    fun deckOptions(deck: ObservableEntity<Deck>)
    fun createDeck()
}

/** A plain text span with extra classes. */
fun Caption(text: String = "", vararg classNames: String): Span = Span(text).apply {
    addClassName("anki-caption")
    addClassNames(*classNames)
}

/** A transparent single-line row of components. */
fun Row(vararg items: Component): Div = Div(*items).apply { addClassName("anki-row") }

fun Spacer(): Div = Div().apply { addClassName("anki-grow") }

/** A borderless icon button; without [onClick] it does nothing. */
fun IconButton(icon: VaadinIcon, tooltip: String, onClick: (() -> Unit)? = null): NativeButton =
    NativeButton().apply {
        addClassName("anki-icon-button")
        add(icon.create())
        element.setAttribute("aria-label", tooltip)
        Tooltip.forComponent(this).withText(tooltip)
        onClick?.let { this.onClick(it) }
    }

/** A text link that runs [onClick] — deck names, "back" links. */
fun LinkButton(text: String = "", onClick: () -> Unit): NativeButton = NativeButton(text).apply {
    addClassName("anki-link")
    this.onClick(onClick)
}

/**
 * One of a deck's three counts, painted in its queue's colour — blue for new,
 * red for learning, green for review — and greyed out at zero, as in Anki.
 */
context(bindings: Bindings)
fun QueueCount(queue: Queue, count: Supplier<Int>): Span = Span().apply {
    addClassNames("anki-count", queue.cssClass)
    text { count.get().toString() }
    className("zero") { count.get() == 0 }
}

val Queue.cssClass: String
    get() = when (this) {
        Queue.NEW -> "anki-new"
        Queue.LEARN -> "anki-learn"
        Queue.REVIEW -> "anki-review"
    }

val Queue.title: String
    get() = when (this) {
        Queue.NEW -> "New"
        Queue.LEARN -> "Learning"
        Queue.REVIEW -> "To Review"
    }

/**
 * Shown once a deck has nothing left for today, with the reasons Anki gives:
 * learning cards due later, new cards held back by the limit, and tomorrow's
 * reviews.
 */
class Congratulations(queue: Supplier<DeckQueue>, onBack: () -> Unit) : Div(), Bindings by Bindings() {

    init {
        addClassName("anki-congrats")

        fun hint(message: () -> String?) = Caption(classNames = arrayOf("anki-hint")).apply {
            text { message().orEmpty() }
            visible { message() != null }
        }

        add(
            Div(VaadinIcon.CHECK_CIRCLE.create()).apply { addClassName("anki-congrats-icon") },
            Caption("Congratulations! You have finished this deck for now.", "anki-congrats-title"),
            hint {
                queue.get().laterLearning.takeIf { it > 0 }
                    ?.let { "${plural(it, "learning card")} will be due later today." }
            },
            hint {
                queue.get().newHeldBack.takeIf { it > 0 }
                    ?.let { "${plural(it, "new card")} held back by today's limit — raise it in Options." }
            },
            hint {
                queue.get().dueTomorrow.takeIf { it > 0 }
                    ?.let { "${plural(it, "review")} due tomorrow. Use Next day in the top bar to get there." }
            },
            LinkButton("Back to decks", onBack)
        )
    }
}

fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"

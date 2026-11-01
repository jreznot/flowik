package demo.vaadin.anki

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.menubar.MenuBarVariant
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.Button
import flowik.vaadin.childSwitch
import flowik.vaadin.className
import flowik.vaadin.enabled
import flowik.vaadin.item
import flowik.vaadin.onClick
import flowik.vaadin.shortcut
import flowik.vaadin.text
import flowik.vaadin.visible

/**
 * Studying a deck: the question, Show Answer, then the answer with the four
 * buttons that schedule the card — Again, Hard, Good, Easy — each labelled with
 * when the card would come back.
 *
 * Nothing here decides which card comes next. The screen shows
 * [AnkiStore.currentCard], which is derived from the deck's queue, so once an
 * answer reschedules the card the next one simply appears.
 *
 * Keys follow Anki: Space or Enter shows the answer and then answers Good,
 * 1–4 press a button, E edits the card, Ctrl+Z undoes and Escape goes back to
 * the overview. The shortcuts end with the screen.
 */
class StudyScreen(
    private val store: AnkiStore,
    private val deck: ObservableEntity<Deck>,
    private val dialogs: AnkiDialogs
) : Div(), Bindings by Bindings() {

    private val hasCard = { store.currentCard != null }

    init {
        addClassName("anki-study")

        val cardArea = Div().apply {
            addClassName("anki-card-area")
            childSwitch(store::currentCard) { card ->
                if (card == null) Congratulations({ store.queueOf(deck) }, onBack = store::showDecks)
                else CardFace(card, answerShown = store::answerShown)
            }
        }

        add(header(), cardArea, bottomBar())
        installShortcuts()
    }

    /** The deck name, back to the overview, and the counts with the current card's queue underlined. */
    private fun header(): Div {
        val back = LinkButton(onClick = { store.showOverview(deck) }).apply {
            add(VaadinIcon.ARROW_LEFT.create(), Span().apply { text { deck[Deck::name] } })
        }
        val counts = Row(
            *Queue.entries.map { q ->
                QueueCount(q) { store.queueOf(deck).count(q) }.apply {
                    className("current") { store.currentCard?.get(Card::schedule)?.state?.queue == q }
                }
            }.toTypedArray()
        ).apply {
            addClassName("anki-study-counts")
            visible(hasCard)
        }
        return Div(back, Spacer(), counts).apply { addClassName("anki-study-header") }
    }

    private fun bottomBar(): Div {
        val edit = Button("Edit") { store.currentCard?.let(dialogs::editCard) }.apply {
            enabled(hasCard)
        }

        val showAnswer = NativeButton("Show Answer").apply {
            addClassName("anki-show-answer")
            onClick { store.showAnswer() }
            visible { hasCard() && !store.answerShown }
        }

        val answers = Row(*Answer.entries.map(::answerButton).toTypedArray()).apply {
            addClassName("anki-answers")
            visible { hasCard() && store.answerShown }
        }

        val undo = Button("Undo") { store.undo() }.apply {
            icon = VaadinIcon.ROTATE_LEFT.create()
            enabled { store.canUndo }
        }

        val more = MenuBar().apply {
            addThemeVariants(MenuBarVariant.ALIGN_END)
            val menu = addItem("More").subMenu
            menu.item("Forget card") { store.currentCard?.let(store::forgetCard) }
            menu.item("Delete card") { store.currentCard?.let(store::deleteCard) }
            enabled(hasCard)
        }

        return Div(
            Row(edit).apply { addClassName("anki-bar-side") },
            Div(showAnswer, answers).apply { addClassName("anki-bar-center") },
            Row(undo, more).apply { addClassNames("anki-bar-side", "end") }
        ).apply { addClassName("anki-bottom-bar") }
    }

    /** `10m` over `Good` — the label is re-derived from the card's schedule on every change. */
    private fun answerButton(answer: Answer) = NativeButton().apply {
        addClassNames("anki-answer", "anki-answer-${answer.name.lowercase()}")
        element.setAttribute("aria-keyshortcuts", answer.key.toString())
        add(
            Span().apply {
                addClassName("anki-answer-interval")
                text { store.answerLabels[answer].orEmpty() }
            },
            Span(answer.title).apply { addClassName("anki-answer-title") }
        )
        onClick { store.answer(answer) }
    }

    private fun installShortcuts() {
        val primary = { if (store.answerShown) store.answer(Answer.GOOD) else store.showAnswer() }
        shortcut(Key.SPACE, handler = primary)
        shortcut(Key.ENTER, handler = primary)

        val digits = listOf(Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4)
        Answer.entries.forEach { answer -> shortcut(digits[answer.key - 1]) { store.answer(answer) } }

        shortcut(Key.KEY_E) { store.currentCard?.let(dialogs::editCard) }
        shortcut(Key.KEY_Z, KeyModifier.CONTROL) { store.undo() }
        shortcut(Key.ESCAPE) { store.showOverview(deck) }
    }
}

/**
 * The card itself: the front, and — once the answer is shown — a rule and the
 * back. Both sides are bound to the card's atoms, so an edit made while the
 * card is on screen shows up at once.
 */
class CardFace(card: ObservableEntity<Card>, answerShown: () -> Boolean) : Div(), Bindings by Bindings() {

    init {
        addClassName("anki-card")
        add(
            Div().apply {
                addClassName("anki-card-front")
                text { card[Card::front] }
            },
            Hr().apply { visible(answerShown) },
            Div().apply {
                addClassName("anki-card-back")
                text { card[Card::back] }
                visible(answerShown)
            }
        )
    }
}

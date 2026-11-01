package demo.anki

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.menubar.MenuBarVariant
import flowik.core.*
import flowik.vaadin.*
import java.util.function.Supplier

/**
 * Studying a deck: the question, Show Answer, then the answer with the four
 * buttons that schedule the card — Again, Hard, Good, Easy — each labelled with
 * when the card would come back.
 *
 * The deck's [queue] is handed in and every answer is reported; nothing here
 * schedules a card. What the screen does keep is the state of the session that
 * nobody else reads — as in Compose, it stays with the component that uses it:
 *
 * - whether the answer is showing;
 * - a card to show before the queue's pick — [resume], the one an undo brought
 *   back — until it is answered.
 *
 * The card on screen is derived from those two and the queue, so once an answer
 * reschedules a card the next one simply appears.
 *
 * Keys follow Anki: Space or Enter shows the answer and then answers Good,
 * 1–4 press a button, E edits the card, Ctrl+Z undoes and Escape goes back to
 * the overview. The shortcuts end with the screen.
 *
 * @param resume         the card to show first, if any — after an undo
 * @param intervalLabels the interval above each answer button for a card,
 *                       e.g. `10m`; read inside a binding, so it may be derived
 * @param onAnswer       the user rated [card]; the owner schedules it
 * @param onBack         back to the deck's overview
 * @param onDecks      back to the deck list, from the "finished" message
 */
class StudyScreen(
    deck: ObservableEntity<Deck>,
    private val queue: Supplier<DeckQueue>,
    resume: ObservableEntity<Card>?,
    intervalLabels: (ObservableEntity<Card>) -> Map<Answer, String>,
    private val canUndo: Supplier<Boolean>,
    private val onAnswer: (ObservableEntity<Card>, Answer) -> Unit,
    private val onUndo: () -> Unit,
    private val onEdit: (ObservableEntity<Card>) -> Unit,
    private val onForget: (ObservableEntity<Card>) -> Unit,
    private val onDelete: (ObservableEntity<Card>) -> Unit,
    private val onBack: () -> Unit,
    onDecks: () -> Unit
) : Div(), Bindings by Bindings() {

    private var answerShown by observable(false, name = "answerShown")

    /** Shown before the queue's pick until it is answered — the card an undo brought back. */
    private var pinned by observableRef(resume, name = "pinned")

    /**
     * The card on screen, or `null` once the deck is done for today. Compared by
     * identity, so it only notifies when another card comes up — not every time
     * the queue is re-derived.
     */
    private val card = register(computedRef { pinned ?: queue.get().next })

    private val answerLabels by computed { card.value?.let(intervalLabels).orEmpty() }

    private val hasCard = { card.value != null }

    init {
        addClassName("anki-study")

        val cardArea = Div().apply {
            addClassName("anki-card-area")
            childSwitch(card) { current ->
                if (current == null) Congratulations(queue, onDecks) else CardFace(current, ::answerShown)
            }
        }

        // Another card coming up — deleted, forgotten, or edited out of the
        // queue — starts with its question, like a newly dealt one.
        reaction("StudyScreen.newCard", supply = { card.value }) { answerShown = false }

        add(header(deck, queue), cardArea, bottomBar())
        installShortcuts()
    }

    /** The deck name, back to the overview, and the counts with the current card's queue underlined. */
    private fun header(deck: ObservableEntity<Deck>, queue: Supplier<DeckQueue>): Div {
        val back = LinkButton(onClick = onBack).apply {
            add(VaadinIcon.ARROW_LEFT.create(), Span().apply { text { deck[Deck::name] } })
        }
        val counts = Row(
            *Queue.entries.map { q ->
                QueueCount(q) { queue.get().count(q) }.apply {
                    className("current") { card.value?.get(Card::schedule)?.state?.queue == q }
                }
            }.toTypedArray()
        ).apply {
            addClassName("anki-study-counts")
            visible(hasCard)
        }
        return Div(back, Spacer(), counts).apply { addClassName("anki-study-header") }
    }

    private fun bottomBar(): Div {
        val edit = Button("Edit") { card.value?.let(onEdit) }.apply {
            enabled(hasCard)
        }

        val showAnswer = NativeButton("Show Answer").apply {
            addClassName("anki-show-answer")
            onClick(::showAnswer)
            visible { hasCard() && !answerShown }
        }

        val answers = Row(*Answer.entries.map(::answerButton).toTypedArray()).apply {
            addClassName("anki-answers")
            visible { hasCard() && answerShown }
        }

        val undo = Button("Undo", ::undo).apply {
            icon = VaadinIcon.ROTATE_LEFT.create()
            enabled(canUndo)
        }

        val more = MenuBar().apply {
            addThemeVariants(MenuBarVariant.ALIGN_END)
            val menu = addItem("More").subMenu
            // Either way the card leaves the session, so it stops being the pinned one.
            menu.item("Forget card") { card.value?.let { pinned = null; onForget(it) } }
            menu.item("Delete card") { card.value?.let { pinned = null; onDelete(it) } }
            enabled(hasCard)
        }

        return Div(
            Row(edit).apply { addClassName("anki-bar-side") },
            Div(showAnswer, answers).apply { addClassName("anki-bar-center") },
            Row(undo, more).apply { addClassNames("anki-bar-side", "end") }
        ).apply { addClassName("anki-bottom-bar") }
    }

    /** `10m` over `Good`. */
    private fun answerButton(answer: Answer) = NativeButton().apply {
        addClassNames("anki-answer", "anki-answer-${answer.name.lowercase()}")
        element.setAttribute("aria-keyshortcuts", answer.key.toString())
        add(
            Span().apply {
                addClassName("anki-answer-interval")
                text { answerLabels[answer].orEmpty() }
            },
            Span(answer.title).apply { addClassName("anki-answer-title") }
        )
        onClick { answer(answer) }
    }

    private fun showAnswer() {
        if (hasCard()) answerShown = true
    }

    /**
     * Rates the card on screen and deals the next one. The question side comes
     * first even when the same card comes straight back, as a learning card can.
     */
    private fun answer(answer: Answer) {
        val current = card.value ?: return
        if (!answerShown) return
        answerShown = false
        pinned = null
        onAnswer(current, answer)
    }

    private fun undo() {
        if (canUndo.get()) onUndo()
    }

    private fun installShortcuts() {
        val primary = { if (answerShown) answer(Answer.GOOD) else showAnswer() }
        shortcut(Key.SPACE, handler = primary)
        shortcut(Key.ENTER, handler = primary)

        val digits = listOf(Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4)
        Answer.entries.forEach { answer -> shortcut(digits[answer.key - 1]) { answer(answer) } }

        shortcut(Key.KEY_E) { card.value?.let(onEdit) }
        shortcut(Key.KEY_Z, KeyModifier.CONTROL, handler = ::undo)
        shortcut(Key.ESCAPE, handler = onBack)
    }
}

/**
 * The card itself: the front, and — once the answer is shown — a rule and the
 * back. Both sides are bound to the card's atoms, so an edit made while the
 * card is on screen shows up at once.
 */
class CardFace(card: ObservableEntity<Card>, answerShown: Supplier<Boolean>) : Div(), Bindings by Bindings() {

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

package demo.vaadin.anki

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.observable
import flowik.core.viewmodel.createViewModel
import flowik.vaadin.*
import java.util.function.Supplier

/**
 * Anki's Add window: pick a deck, type both sides, Add — and the dialog stays
 * open for the next card, as Anki's does. Ctrl+Enter adds too.
 *
 * What is being typed is the dialog's own state, since nothing else reads it;
 * the finished card is reported to [onAdd], and the fields are cleared.
 *
 * @param decks       the decks to choose from; one created meanwhile shows up
 * @param initialDeck the deck selected when the dialog opens
 */
class AddCardsDialog(
    decks: Supplier<List<ObservableEntity<Deck>>>,
    initialDeck: ObservableEntity<Deck>?,
    private val onAdd: (deck: ObservableEntity<Deck>, front: String, back: String) -> Unit
) : Dialog(), Bindings by Bindings() {

    private val front = observable("", name = "front")
    private val back = observable("", name = "back")
    private val added = observable(0, name = "added")

    private val deckSelect = Select<ObservableEntity<Deck>>().apply {
        label = "Deck"
        setItemLabelGenerator { it[Deck::name] }
        items(decks)
        value = initialDeck
        setWidthFull()
    }

    private val frontField = side("Front", front)

    init {
        headerTitle = "Add"
        width = "520px"

        val status = Caption(classNames = arrayOf("anki-muted")).apply {
            text { if (added.value == 0) "Ctrl+Enter adds the card." else "Added ${plural(added.value, "card")}." }
        }

        add(Div(deckSelect, frontField, side("Back", back), status).apply { addClassName("anki-form") })

        val add = Button("Add") { addCard() }.apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClickShortcut(Key.ENTER, KeyModifier.CONTROL)
            enabled { front.value.isNotBlank() && back.value.isNotBlank() }
        }
        footer.add(Button("Close") { close() }, add)

        addOpenedChangeListener { if (it.isOpened) frontField.focus() }
        disposeOnDetach()
    }

    private fun side(label: String, text: MutableObservable<String>) = TextArea(label).apply {
        setWidthFull()
        minRows = 2
        // Eager, so Add is enabled as soon as both sides have text.
        valueChangeMode = ValueChangeMode.EAGER
        value(text)
    }

    private fun addCard() {
        val target = deckSelect.value ?: return
        if (front.value.isBlank() || back.value.isBlank()) return
        onAdd(target, front.value, back.value)
        front.value = ""
        back.value = ""
        added.value += 1
        frontField.focus()
    }
}

/**
 * Edits one card. The fields write to a buffer over [card], so the card itself
 * is never touched from here: Save reports both sides to [onSave], and Cancel
 * simply drops the buffer. The card info below follows the live schedule.
 *
 * @param deckName the name of the card's deck, shown in the card info
 */
class EditCardDialog(
    card: ObservableEntity<Card>,
    deckName: Supplier<String>,
    onSave: (front: String, back: String) -> Unit
) : Dialog(), Bindings by Bindings() {

    private val form = createViewModel(card)

    init {
        headerTitle = "Edit card"
        width = "520px"

        val front = TextArea("Front").apply {
            setWidthFull()
            minRows = 2
            valueChangeMode = ValueChangeMode.EAGER
            value(form.property(Card::front))
        }
        val back = TextArea("Back").apply {
            setWidthFull()
            minRows = 2
            valueChangeMode = ValueChangeMode.EAGER
            value(form.property(Card::back))
        }

        val info = Caption(classNames = arrayOf("anki-muted", "anki-card-info")).apply {
            text {
                val s = card[Card::schedule]
                buildList {
                    add(deckName.get())
                    add(s.state.title)
                    if (s.intervalDays > 0) add("interval ${formatDays(s.intervalDays)}")
                    if (s.ease > 0) add("ease ${(s.ease * 100).toInt()}%")
                    add(plural(s.reps, "review"))
                    if (s.lapses > 0) add(plural(s.lapses, "lapse"))
                }.joinToString(" · ")
            }
        }

        add(Div(front, back, info).apply { addClassName("anki-form") })

        footer.add(
            Button("Cancel") { close() },
            Button("Save") {
                onSave(form[Card::front], form[Card::back])
                close()
            }.apply {
                addThemeVariants(ButtonVariant.PRIMARY)
                addClickShortcut(Key.ENTER, KeyModifier.CONTROL)
                enabled { form.isDirty && form[Card::front].isNotBlank() && form[Card::back].isNotBlank() }
            }
        )

        addOpenedChangeListener { if (it.isOpened) front.focus() }
        // A closed dialog is detached and never reopened: its bindings go with it.
        disposeOnDetach()
    }
}

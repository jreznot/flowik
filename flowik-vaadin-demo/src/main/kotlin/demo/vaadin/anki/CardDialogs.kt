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
import flowik.vaadin.Button
import flowik.vaadin.disposeOnDetach
import flowik.vaadin.enabled
import flowik.vaadin.items
import flowik.vaadin.text
import flowik.vaadin.value

/**
 * Anki's Add window: pick a deck, type both sides, Add — and the dialog stays
 * open for the next card, as Anki's does. Ctrl+Enter adds too.
 *
 * Each side is an atom local to the dialog; Add hands them to the store and
 * clears them.
 */
class AddCardsDialog(
    private val store: AnkiStore,
    deck: ObservableEntity<Deck>?
) : Dialog(), Bindings by Bindings() {

    private val front = observable("", name = "front")
    private val back = observable("", name = "back")
    private val added = observable(0, name = "added")

    private val deckSelect = Select<ObservableEntity<Deck>>().apply {
        label = "Deck"
        setItemLabelGenerator { it[Deck::name] }
        items { store.decks.items }
        value = deck ?: store.decks.items.firstOrNull()
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
        if (store.addCard(target, front.value, back.value) == null) return
        front.value = ""
        back.value = ""
        added.value += 1
        frontField.focus()
    }
}

/**
 * Edits one card through a buffer: the fields write to a view model, and only
 * Save submits it to the card. The card info below follows the live schedule.
 */
class EditCardDialog(
    store: AnkiStore,
    card: ObservableEntity<Card>
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
                val deckName = store.deckOf(card)?.get(Deck::name).orEmpty()
                buildList {
                    add(deckName)
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
                form.submit()
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

package demo.vaadin.anki

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.core.observable
import flowik.core.viewmodel.createViewModel
import flowik.vaadin.Button
import flowik.vaadin.disposeOnDetach
import flowik.vaadin.enabled
import flowik.vaadin.errorMessage
import flowik.vaadin.value
import kotlin.reflect.KProperty1

/**
 * Asks for the name of a new deck, and says as it is typed when the name is
 * already taken.
 *
 * @param isNameTaken checked on every keystroke; it reads the decks, so a deck
 *                    created or renamed meanwhile is taken into account
 * @param onCreate    creates the deck from a name that passed the check
 */
class CreateDeckDialog(
    isNameTaken: (String) -> Boolean,
    onCreate: (String) -> Unit
) : Dialog(), Bindings by Bindings() {

    private val deckName = observable("", name = "deckName")

    private val nameError = {
        deckName.value.trim().takeIf(isNameTaken)?.let { "A deck named '$it' already exists." }
    }

    private val name = TextField("Deck name").apply {
        setWidthFull()
        valueChangeMode = ValueChangeMode.EAGER
        value(deckName)
        errorMessage(nameError)
    }

    init {
        headerTitle = "Create Deck"
        width = "360px"
        add(name)

        val create = Button("Create") {
            onCreate(deckName.value.trim())
            close()
        }.apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClickShortcut(Key.ENTER)
            enabled { deckName.value.isNotBlank() && nameError() == null }
        }

        footer.add(Button("Cancel") { close() }, create)
        addOpenedChangeListener { if (it.isOpened) name.focus() }
        disposeOnDetach()
    }
}

/**
 * A deck's name, description and daily limits, edited through a buffer and
 * saved together. Lowering a limit takes effect at once: the deck's counts are
 * derived from it.
 */
class DeckOptionsDialog(
    private val store: AnkiStore,
    private val deck: ObservableEntity<Deck>
) : Dialog(), Bindings by Bindings() {

    private val form = createViewModel(deck)

    private val nameError = {
        val name = form[Deck::name].trim()
        when {
            name.isEmpty() -> "A deck needs a name."
            store.isDeckNameTaken(name, except = deck) -> "A deck named '$name' already exists."
            else -> null
        }
    }

    init {
        headerTitle = "Deck Options"
        width = "440px"

        val name = TextField("Name").apply {
            setWidthFull()
            valueChangeMode = ValueChangeMode.EAGER
            value(form.property(Deck::name))
            errorMessage(nameError)
        }

        val description = TextArea("Description").apply {
            setWidthFull()
            value(form.property(Deck::description))
        }

        add(
            Div(
                name,
                description,
                limit("New cards/day", Deck::newPerDay),
                limit("Maximum reviews/day", Deck::reviewsPerDay)
            ).apply { addClassName("anki-form") }
        )

        val delete = Button("Delete Deck") { confirmDelete() }.apply {
            addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY)
            addClassName("anki-footer-start")
        }

        footer.add(
            delete,
            Button("Cancel") { close() },
            Button("Save") {
                form.submit()
                close()
            }.apply {
                addThemeVariants(ButtonVariant.PRIMARY)
                enabled { form.isDirty && nameError() == null }
            }
        )

        disposeOnDetach()
    }

    /**
     * A non-negative count. The field reports `null` while it is cleared; the
     * controlled binding keeps the last valid limit in the buffer instead.
     */
    private fun limit(label: String, property: KProperty1<Deck, Int>) = IntegerField(label).apply {
        setWidthFull()
        min = 0
        max = 9999
        isStepButtonsVisible = true
        value({ form[property] }) { value -> if (value != null && value >= 0) form[property] = value }
    }

    private fun confirmDelete() {
        val count = store.cardsOf(deck).size
        Dialog().apply {
            headerTitle = "Delete deck?"
            add(Paragraph("'${deck[Deck::name]}' and its ${plural(count, "card")} will be deleted."))
            footer.add(
                Button("Cancel") { close() },
                Button("Delete") {
                    store.deleteDeck(deck)
                    close()
                    this@DeckOptionsDialog.close()
                }.apply { addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR) }
            )
        }.open()
    }
}

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
import flowik.core.*
import flowik.core.viewmodel.createViewModel
import flowik.vaadin.*
import java.util.function.Supplier
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

    private val deckName by observable("", name = "deckName")
    private val nameError by computed {
        deckName.trim().takeIf(isNameTaken)?.let { "A deck named '$it' already exists." }
    }

    private val name = TextField("Deck name").apply {
        setWidthFull()
        valueChangeMode = ValueChangeMode.EAGER
        value(::deckName.asObservable())
        errorMessage(::nameError)
    }

    init {
        headerTitle = "Create Deck"
        width = "360px"
        add(name)

        val create = Button("Create") {
            onCreate(deckName.trim())
            close()
        }.apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClickShortcut(Key.ENTER)
            enabled { deckName.isNotBlank() && nameError == null }
        }

        footer.add(Button("Cancel") { close() }, create)
        addOpenedChangeListener { if (it.isOpened) name.focus() }
        disposeOnDetach()
    }
}

/**
 * A deck's name, description and daily limits, edited through a buffer over
 * [deck] and saved together. The deck itself is never touched from here: Save
 * reports the new settings to [onSave], and Delete — once confirmed — to
 * [onDelete].
 *
 * @param cardCount   how many cards the deck holds, for the delete confirmation
 * @param isNameTaken whether another deck already uses a name
 * @param onSave      receives the deck with its new name, description and limits
 */
class DeckOptionsDialog(
    private val deck: ObservableEntity<Deck>,
    private val cardCount: Supplier<Int>,
    isNameTaken: (String) -> Boolean,
    onSave: (Deck) -> Unit,
    private val onDelete: () -> Unit
) : Dialog(), Bindings by Bindings() {

    private val form = createViewModel(deck)

    private val nameError by computed {
        val name = form[Deck::name].trim()
        when {
            name.isEmpty() -> "A deck needs a name."
            isNameTaken(name) -> "A deck named '$name' already exists."
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
            errorMessage(::nameError)
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
                onSave(
                    deck.snapshot.copy(
                        name = form[Deck::name],
                        description = form[Deck::description],
                        newPerDay = form[Deck::newPerDay],
                        reviewsPerDay = form[Deck::reviewsPerDay]
                    )
                )
                close()
            }.apply {
                addThemeVariants(ButtonVariant.PRIMARY)
                enabled { form.isDirty && nameError == null }
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
        Dialog().apply {
            headerTitle = "Delete deck?"
            add(Paragraph("'${deck[Deck::name]}' and its ${plural(cardCount.get(), "card")} will be deleted."))
            footer.add(
                Button("Cancel") { close() },
                Button("Delete") {
                    onDelete()
                    close()
                    this@DeckOptionsDialog.close()
                }.apply { addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR) }
            )
        }.open()
    }
}

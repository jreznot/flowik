package demo.vaadin.obsidian

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.textfield.TextField
import flowik.vaadin.Button

/**
 * Asks for the name of a new note — the sidebar's "New note" button, where
 * Swing has `JOptionPane.showInputDialog`.
 *
 * @param onCreate creates the note; returning `false` means the name is taken,
 *                 and the dialog stays open to say so
 */
class NewNoteDialog(onCreate: (String) -> Boolean) : Dialog() {

    private val name = TextField("Note name").apply {
        value = "Untitled"
        setWidthFull()
        isAutoselect = true
    }

    init {
        headerTitle = "New note"
        width = "360px"
        add(name)

        val create = Button("Create") {
            val text = name.value.trim()
            when {
                text.isEmpty() -> close()
                onCreate(text) -> close()
                else -> {
                    name.errorMessage = "A note named '$text' already exists."
                    name.isInvalid = true
                }
            }
        }.apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClickShortcut(Key.ENTER)
        }

        footer.add(Button("Cancel") { close() }, create)
        name.addValueChangeListener { name.isInvalid = false }
        addOpenedChangeListener { if (it.isOpened) name.focus() }
    }
}

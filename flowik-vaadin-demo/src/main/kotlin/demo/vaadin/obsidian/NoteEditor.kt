package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.text
import flowik.vaadin.value

/**
 * The editor for one note: an inline title bar over a text area bound two-way
 * to the note's `content` atom.
 *
 * Because the text lives in the observable and not in the widget, an editor can
 * be thrown away and rebuilt — as it is every time another tab is selected —
 * without losing what was typed.
 */
class NoteEditor(note: ObservableEntity<Note>) : Div(), Bindings by Bindings() {

    private val textArea = TextArea().apply {
        addClassName("obs-editor-text")
        // Sent after a pause in typing rather than on every key.
        valueChangeMode = ValueChangeMode.LAZY
        value(note.property(Note::content))
    }

    init {
        addClassName("obs-editor")

        val inlineTitle = Caption(classNames = arrayOf("obs-inline-title")).apply {
            text { note[Note::name].removeSuffix(".md") }
        }
        val header = Div(
            Row(
                IconButton(VaadinIcon.ARROW_LEFT, "Navigate back", small = true),
                IconButton(VaadinIcon.ARROW_RIGHT, "Navigate forward", small = true)
            ),
            inlineTitle,
            Row(IconButton(VaadinIcon.ELLIPSIS_H, "More options", small = true)).apply { addClassName("end") }
        ).apply { addClassName("obs-editor-header") }

        add(header, Div(textArea).apply { addClassName("obs-editor-scroll") })
    }

    fun focusEditor() {
        textArea.focus()
    }
}

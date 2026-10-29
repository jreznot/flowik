package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.activeWhen
import flowik.vaadin.items
import flowik.vaadin.onClick
import flowik.vaadin.onDomEvent
import flowik.vaadin.text
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * The list of note files, with a placeholder for when it is empty.
 *
 * @param notes      the notes to show — pass a filtered derivation and the list
 *                   follows the filter with no extra wiring
 * @param activeNote drives the highlight, so the list follows the open tab
 * @param onOpen     invoked on click or Enter
 */
class NoteListView(
    notes: Supplier<List<ObservableEntity<Note>>>,
    activeNote: Supplier<ObservableEntity<Note>?>,
    emptyText: String = "No notes found",
    onOpen: (ObservableEntity<Note>) -> Unit
) : Div(), Bindings by Bindings() {

    init {
        addClassName("obs-note-list")

        add(
            Div().apply {
                element.setAttribute("role", "listbox")
                items(notes) { note ->
                    NoteRow(note, isActive = { activeNote.get() === note }, onOpen = { onOpen(note) })
                }
            },
            Caption(emptyText, "obs-placeholder").apply {
                visible { notes.get().isEmpty() }
            }
        )
    }
}

/**
 * Renders a note file row: [icon] name. It owns its bindings — the list
 * rebuilds its rows as the filter changes and disposes the ones it drops.
 */
class NoteRow(
    note: ObservableEntity<Note>,
    isActive: Supplier<Boolean>,
    onOpen: () -> Unit
) : Div(), Bindings by Bindings() {

    init {
        addClassName("obs-note-row")
        element.setAttribute("role", "option")
        element.setAttribute("tabindex", "0")
        add(VaadinIcon.FILE_TEXT.create(), Span().apply { text { note[Note::name] } })

        activeWhen(provider = isActive)

        onClick(onOpen)
        onDomEvent("keydown", filter = "event.key === 'Enter'") { onOpen() }
    }
}

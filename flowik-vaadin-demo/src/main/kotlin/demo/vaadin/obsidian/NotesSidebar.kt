package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.toggle
import flowik.vaadin.className
import flowik.vaadin.text
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * The collapsible left sidebar: file actions, the filter field, a section
 * header and the note list, over a vault footer.
 *
 * The panel is a shell — the list is a [NoteListView] and the query field a
 * [FilterField], both usable on their own. It is always visible as far as it is
 * concerned; wrap it in a [SlidingPanel] to make it collapsible.
 *
 * Both children are `register`ed, so the sidebar's own `dispose()` takes their
 * bindings with it and the owner above only has to release one thing.
 *
 * @param sectionTitle   header text, shown upper-cased
 * @param notes          the notes to list (already filtered)
 * @param activeNote     drives the list highlight
 * @param noteCountText  counter next to the header, e.g. `3 of 12 notes`
 * @param notesVisible   `false` swaps the list for a "not part of this demo"
 *                       placeholder and hides the counter, which is how the
 *                       non-file rail tools are handled
 * @param filterText     two-way bound query
 * @param filterVisible  toggled by the filter button, which is drawn active
 *                       while the field is showing
 */
class NotesSidebar(
    private val sectionTitle: Supplier<String>,
    notes: Supplier<List<ObservableEntity<Note>>>,
    activeNote: Supplier<ObservableEntity<Note>?>,
    noteCountText: Supplier<String>,
    private val notesVisible: Supplier<Boolean>,
    filterText: MutableObservable<String>,
    filterVisible: MutableObservable<Boolean>,
    onCreateNote: () -> Unit,
    onOpenNote: (ObservableEntity<Note>) -> Unit,
    vaultName: String = "Flowik Vault"
) : Div(), Bindings by Bindings() {

    val filterField = register(FilterField(filterText, filterVisible))

    init {
        addClassName("obs-sidebar")
        add(
            header(noteCountText, filterVisible, onCreateNote),
            content(notes, activeNote, onOpenNote),
            footer(vaultName)
        )
    }

    private fun header(
        noteCountText: Supplier<String>,
        filterVisible: MutableObservable<Boolean>,
        onCreateNote: () -> Unit
    ): Div {
        val actions = Row(
            IconButton(VaadinIcon.FILE_ADD, "New note", onClick = onCreateNote),
            IconButton(VaadinIcon.FOLDER_ADD, "New folder"),
            IconButton(VaadinIcon.ANGLE_DOUBLE_UP, "Collapse all"),
            IconButton(VaadinIcon.SORT, "Change sort order"),
            IconButton(VaadinIcon.FILTER, "Filter notes by name") { filterVisible.toggle() }
                .apply { className("active") { filterVisible.value } }
        ).apply { addClassName("obs-sidebar-actions") }

        val title = Caption(classNames = arrayOf("obs-section-title")).apply {
            text { sectionTitle.get().uppercase() }
        }
        val count = Caption().apply {
            text(noteCountText)
            visible(notesVisible)
        }

        return Div(
            actions,
            filterField,
            Div(title, count).apply { addClassName("obs-section-header") }
        )
    }

    /** The note list, or a placeholder for the rail tools this demo fakes. */
    private fun content(
        notes: Supplier<List<ObservableEntity<Note>>>,
        activeNote: Supplier<ObservableEntity<Note>?>,
        onOpenNote: (ObservableEntity<Note>) -> Unit
    ): Div = Div(
        register(NoteListView(notes, activeNote, onOpen = onOpenNote)).apply { visible(notesVisible) },
        Caption(classNames = arrayOf("obs-placeholder")).apply {
            text { "${sectionTitle.get()} is not part of this demo" }
            visible { !notesVisible.get() }
        }
    ).apply { addClassName("obs-sidebar-content") }

    private fun footer(vaultName: String): Div = Div(
        Row(
            IconButton(VaadinIcon.ARROWS_LONG_V, "Switch vault", small = true),
            Caption(vaultName, "obs-vault-name")
        ),
        Row(
            IconButton(VaadinIcon.INFO_CIRCLE, "Help", small = true),
            IconButton(VaadinIcon.COG, "Settings", small = true)
        )
    ).apply { addClassName("obs-sidebar-footer") }
}

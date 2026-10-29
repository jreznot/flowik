package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.ObservableList
import flowik.core.untracked
import flowik.vaadin.childSwitch
import flowik.vaadin.items
import flowik.vaadin.visible
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The tabbed working area: a [NoteTab] per open note over the [NoteEditor] of
 * the active one, and Obsidian's centred links while nothing is open.
 *
 * Tabs animate in and out: a new one expands into place, and a closed one
 * collapses *before* [onCloseNote] is reported, so the model only changes once
 * the tab has visually gone. Closing therefore has to go through [closeTab] —
 * removing a note from [openNotes] directly still works, it just skips the
 * animation.
 *
 * @param openNotes    the open notes, in tab order — tabs are added and removed
 *                     to match, one at a time, so only a new tab animates
 * @param activeNote   two-way: clicking a tab writes it, and the editor below
 *                     follows it
 * @param onCreateNote invoked by the `+` button and the empty-state link
 * @param onCloseNote  invoked once a tab has finished collapsing; closing is the
 *                     owner's decision because it also picks the next active note
 * @param onFindFile   invoked by the "Go to file" link
 */
class EditorTabs(
    openNotes: ObservableList<ObservableEntity<Note>>,
    private val activeNote: MutableObservable<ObservableEntity<Note>?>,
    private val onCreateNote: () -> Unit,
    private val onCloseNote: (ObservableEntity<Note>) -> Unit,
    private val onFindFile: () -> Unit
) : Div(), Bindings by Bindings() {

    private val tabList = Div().apply {
        addClassName("obs-tab-list")
        element.setAttribute("role", "tablist")
        items(openNotes) { note ->
            NoteTab(
                note,
                isActive = { activeNote.value === note },
                onSelect = { activeNote.value = note },
                onClose = { closeTab(note) }
            )
        }
    }

    /** Notes whose tab is collapsing; they are still open until it finishes. */
    private val closing: MutableSet<ObservableEntity<Note>> = Collections.newSetFromMap(IdentityHashMap())

    init {
        addClassName("obs-editor-area")

        val tabStrip = Div(
            tabList,
            IconButton(VaadinIcon.PLUS, "New note", onClick = onCreateNote).apply { addClassName("obs-new-tab") }
        ).apply {
            addClassName("obs-tab-strip")
            visible { openNotes.size > 0 }
        }

        val content = Div().apply {
            addClassName("obs-editor-host")
            childSwitch(activeNote) { note ->
                if (note == null) emptyState() else NoteEditor(note).apply { focusEditor() }
            }
        }

        add(tabStrip, content)

        // Making a collapsing tab active again means it is being reopened: call
        // off the close and let it grow back.
        autoRun("editorTabs.reopen") {
            val active = activeNote.value ?: return@autoRun
            untracked {
                if (closing.remove(active)) tabFor(active)?.expand()
            }
        }
    }

    /**
     * Collapses the tab of [note] and reports the close once it is gone. A note
     * whose tab is already collapsing is left alone.
     */
    fun closeTab(note: ObservableEntity<Note>) {
        if (!closing.add(note)) return

        val tab = tabFor(note)
        if (tab == null) {
            // No tab to animate — close straight away.
            closing.remove(note)
            onCloseNote(note)
            return
        }

        tab.collapse {
            closing.remove(note)
            onCloseNote(note)
        }
    }

    /** Closes the active tab — what Alt+W is wired to. */
    fun closeSelectedTab() {
        activeNote.value?.let(::closeTab)
    }

    private fun tabFor(note: ObservableEntity<Note>): NoteTab? =
        tabList.children.filter { it is NoteTab && it.note === note }.findFirst().orElse(null) as NoteTab?

    /** Shown while no note is open — the reference window's centred links. */
    private fun emptyState() = Div(
        LinkLabel("Create new note (${shortcutHint("N")})", onClick = onCreateNote),
        LinkLabel("Go to file (${shortcutHint("O")})", onClick = onFindFile)
    ).apply { addClassName("obs-empty-state") }
}

package demo.swing.obsidian

import flowik.core.Computed
import flowik.core.ObservableEntity
import flowik.core.ObservableList
import flowik.core.Store
import flowik.core.action
import flowik.core.computed
import flowik.core.filter
import flowik.core.observable
import flowik.core.observableRef
import flowik.core.observables

/** The rail tool whose sidebar section lists the note files. */
const val FILES_TOOL = "Files"

/** Base name for notes created without asking for one. */
private const val UNTITLED = "Untitled"

/**
 * Every piece of application state, as observables. The components in this
 * package never see this class: the window hands each of them the individual
 * observables and actions it needs, so a component can be reused with any
 * other state source.
 */
class NotesStore : Store {

    /** All note files, each decomposed into per-property atoms. */
    val notes = observables<Note>()

    /** The notes currently open in the tabbed area, in tab order. */
    val openNotes = ObservableList<ObservableEntity<Note>>()

    var filterText by observable("", name = "filterText")
    var filterVisible by observable(false, name = "filterVisible")
    var leftVisible by observable(true, name = "leftVisible")
    var rightVisible by observable(true, name = "rightVisible")
    var activeTool by observable(FILES_TOOL, name = "activeTool")

    /** The note shown by the selected tab, or `null` when nothing is open. */
    var activeNote by observableRef<ObservableEntity<Note>?>(null, name = "activeNote")

    /**
     * Filtering is only applied while the filter field is visible, so hiding it
     * immediately restores the full list without clearing what was typed.
     */
    val visibleNotes: Computed<List<ObservableEntity<Note>>> = notes.filter { note ->
        val query = if (filterVisible) filterText.trim().lowercase() else ""
        query.isEmpty() || note[Note::name].lowercase().contains(query)
    }

    val noteCount by computed { notes.size }
    val visibleCount by computed { visibleNotes.value.size }

    val countText by computed {
        when {
            noteCount == 0 -> "no notes"
            visibleCount == noteCount -> "$noteCount notes"
            else -> "$visibleCount of $noteCount notes"
        }
    }

    val activeNoteName by computed { activeNote?.get(Note::name) ?: "Flowik Vault" }

    val filesToolSelected by computed { activeTool == FILES_TOOL }

    /**
     * Creates a note file and opens it. Returns `null` when the name is empty
     * or already taken, so the caller can report it.
     *
     * [content] defaults to a heading made from the file name.
     */
    fun createNote(rawName: String, content: String? = null): ObservableEntity<Note>? = action {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return@action null

        val fileName = if (trimmed.endsWith(".md")) trimmed else "$trimmed.md"
        if (isNameTaken(fileName)) return@action null

        notes.add(Note(fileName, content ?: "# ${fileName.removeSuffix(".md")}\n\n"))
        val created = notes[notes.size - 1]
        openNote(created)
        created
    }

    /**
     * Creates and opens an empty `Untitled` note, naming it without asking —
     * `Untitled`, `Untitled 1`, `Untitled 2`, … — the way Obsidian's new-tab
     * button does.
     */
    fun createUntitledNote(): ObservableEntity<Note>? = createNote(nextUntitledName(), content = "")

    /** The first `Untitled…` name no note is using. */
    private fun nextUntitledName(): String {
        var index = 0
        while (true) {
            val name = if (index == 0) UNTITLED else "$UNTITLED $index"
            if (!isNameTaken("$name.md")) return name
            index++
        }
    }

    private fun isNameTaken(fileName: String) =
        notes.items.any { it[Note::name].equals(fileName, ignoreCase = true) }

    fun openNote(note: ObservableEntity<Note>) = action {
        if (openNotes.items.none { it === note }) openNotes.add(note)
        activeNote = note
    }

    /** Closes a tab and activates its neighbour, mirroring Obsidian. */
    fun closeNote(note: ObservableEntity<Note>) = action {
        val index = openNotes.items.indexOfFirst { it === note }
        if (index < 0) return@action

        openNotes.removeAt(index)
        if (activeNote === note) {
            val remaining = openNotes.items
            activeNote = remaining.getOrNull(index.coerceAtMost(remaining.size - 1))
        }
    }

    fun showFilter() = action {
        filterVisible = true
    }
}

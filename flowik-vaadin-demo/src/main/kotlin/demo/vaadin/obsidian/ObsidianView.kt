package demo.vaadin.obsidian

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import flowik.core.Bindings
import flowik.core.asObservable
import flowik.vaadin.disposeOnDetach
import flowik.vaadin.shortcut

private const val FLOWIK_URL = "https://github.com/jreznot/flowik"

/** The rail tools. Only [FILES_TOOL] has a section behind it in this demo. */
private val RAIL_TOOLS = listOf(
    ToolIcon(VaadinIcon.FILE_TEXT, FILES_TOOL),
    ToolIcon(VaadinIcon.SITEMAP, "Graph view"),
    ToolIcon(VaadinIcon.GRID, "Canvas"),
    ToolIcon(VaadinIcon.CALENDAR, "Daily notes"),
    ToolIcon(VaadinIcon.COPY, "Templates"),
    ToolIcon(VaadinIcon.TERMINAL, "Command palette"),
    ToolIcon(VaadinIcon.TASKS, "Outline"),
    ToolIcon(VaadinIcon.TAG, "Tags")
)

private val RAIL_FOOTER_TOOLS = listOf(
    ToolIcon(VaadinIcon.LIFEBUOY, "Help"),
    ToolIcon(VaadinIcon.COG, "Settings")
)

private val TOP_BAR_LEFT_ICONS = listOf(
    ToolIcon(VaadinIcon.FOLDER_OPEN, "Open another vault"),
    ToolIcon(VaadinIcon.SEARCH, "Search all notes"),
    ToolIcon(VaadinIcon.BOOKMARK, "Bookmarks")
)

private val TOP_BAR_RIGHT_ICONS = listOf(
    ToolIcon(VaadinIcon.LINK, "Backlinks"),
    ToolIcon(VaadinIcon.EXTERNAL_LINK, "Outgoing links"),
    ToolIcon(VaadinIcon.TAGS, "Tags"),
    ToolIcon(VaadinIcon.ARCHIVE, "Archive"),
    ToolIcon(VaadinIcon.LIST, "Outline")
)

/**
 * Assembles the page out of the components in this package — the Vaadin
 * counterpart of the Swing demo's `ObsidianWindow`.
 *
 * This is the only place that knows about [NotesStore]: each component is
 * handed the observables and actions it needs, so nothing below depends on how
 * — or where — the state is kept.
 *
 * It is also the root of the UI's lifetime: every panel it builds is
 * `register`ed here, and the group is released when the view is detached, which
 * for a route means the user navigated away.
 */
@Route("obsidian")
@PageTitle("Flowik Vault — Obsidian")
@StyleSheet("obsidian.css")
class ObsidianView(private val store: NotesStore) : Div(), Bindings by Bindings() {

    private val editor = EditorTabs(
        openNotes = store.openNotes,
        activeNote = store::activeNote.asObservable(),
        // The `+` button and the empty-state link name the file themselves.
        onCreateNote = { store.createUntitledNote() },
        onCloseNote = store::closeNote,
        onFindFile = ::goToFile
    )

    private val sidebar = NotesSidebar(
        sectionTitle = store::activeTool,
        notes = store.visibleNotes,
        activeNote = store::activeNote,
        noteCountText = store::countText,
        notesVisible = store::filesToolSelected,
        filterText = store::filterText.asObservable(),
        filterVisible = store::filterVisible.asObservable(),
        // The sidebar's own button still asks for a name.
        onCreateNote = ::promptForNewNote,
        onOpenNote = store::openNote
    )

    private val rail = IconRail(
        tools = RAIL_TOOLS,
        selectedTool = store::activeTool.asObservable(),
        footerTools = RAIL_FOOTER_TOOLS,
        onSelect = { store.leftVisible = true }
    )

    private val backlinks = BacklinksPanel(linkText = "Visit Flowik Web Site", href = FLOWIK_URL)

    private val topBar = TopBar(
        title = store::activeNoteName,
        leftPanelVisible = store::leftVisible.asObservable(),
        rightPanelVisible = store::rightVisible.asObservable(),
        leftIcons = TOP_BAR_LEFT_ICONS,
        rightIcons = TOP_BAR_RIGHT_ICONS
    )

    init {
        // Every panel with reactions of its own mixes in Bindings, and so is a
        // Disposable. BacklinksPanel is static, so there is nothing to own.
        listOf(rail, sidebar, editor, topBar).forEach { register(it) }

        addClassName("obsidian")

        // The rail is a sibling of the sidebar, so collapsing the sidebar
        // leaves the rail in place. Each sidebar slides behind its own edge.
        val main = Div(
            rail,
            SlidingPanel(sidebar, SlideSide.LEFT, store::leftVisible, width = "260px"),
            editor,
            SlidingPanel(backlinks, SlideSide.RIGHT, store::rightVisible, width = "280px")
        ).apply { addClassName("obs-main") }

        add(topBar, main)
        installShortcuts()
        disposeOnDetach()
    }

    /** Reveals the filter field and puts the caret in it, even if it was already up. */
    private fun goToFile() {
        store.leftVisible = true
        store.activeTool = FILES_TOOL
        store.showFilter()
        sidebar.filterField.focusField()
    }

    private fun promptForNewNote() {
        NewNoteDialog { name -> store.createNote(name) != null }.open()
    }

    /**
     * Browsers reserve ⌘/Ctrl+N, O and W for themselves, so the page binds
     * Alt instead. The shortcuts listen on the whole UI and end with the view.
     */
    private fun installShortcuts() {
        shortcut(Key.KEY_N, KeyModifier.ALT) { store.createUntitledNote() }
        shortcut(Key.KEY_O, KeyModifier.ALT, handler = ::goToFile)
        shortcut(Key.KEY_W, KeyModifier.ALT, handler = editor::closeSelectedTab)
        shortcut(Key.BACKSLASH, KeyModifier.ALT) { store.leftVisible = !store.leftVisible }
        shortcut(Key.BACKSLASH, KeyModifier.ALT, KeyModifier.SHIFT) { store.rightVisible = !store.rightVisible }
    }
}

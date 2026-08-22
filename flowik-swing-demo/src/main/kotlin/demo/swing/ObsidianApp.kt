package demo.swing

import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme
import demo.swing.obsidian.BacklinksPanel
import demo.swing.obsidian.EditorTabs
import demo.swing.obsidian.FILES_TOOL
import demo.swing.obsidian.IconRail
import demo.swing.obsidian.NotesSidebar
import demo.swing.obsidian.NotesStore
import demo.swing.obsidian.SlideSide
import demo.swing.obsidian.SlidingPanel
import demo.swing.obsidian.ToolIcon
import demo.swing.obsidian.TopBar
import demo.swing.obsidian.applyObsidianTheme
import demo.swing.obsidian.openInBrowser
import flowik.core.Bindings
import flowik.core.action
import flowik.core.toggle
import flowik.core.unwrapBinding
import flowik.layout.uiFrame
import flowik.swing.disposeOnClose
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

fun main() {
    obsidianDemo()
}

private const val FLOWIK_URL = "https://github.com/jreznot/flowik"

/** The rail tools. Only [FILES_TOOL] has a section behind it in this demo. */
private val RAIL_TOOLS = listOf(
    ToolIcon(CoreUiFree.FILE, FILES_TOOL),
    ToolIcon(CoreUiFree.SITEMAP, "Graph view"),
    ToolIcon(CoreUiFree.GRID, "Canvas"),
    ToolIcon(CoreUiFree.CALENDAR, "Daily notes"),
    ToolIcon(CoreUiFree.COPY, "Templates"),
    ToolIcon(CoreUiFree.TERMINAL, "Command palette"),
    ToolIcon(CoreUiFree.TASK, "Outline"),
    ToolIcon(CoreUiFree.TAG, "Tags")
)

private val RAIL_FOOTER_TOOLS = listOf(
    ToolIcon(CoreUiFree.LIFE_RING, "Help"),
    ToolIcon(CoreUiFree.COG, "Settings")
)

private val TOP_BAR_LEFT_ICONS = listOf(
    ToolIcon(CoreUiFree.FOLDER_OPEN, "Open another vault"),
    ToolIcon(CoreUiFree.MAGNIFYING_GLASS, "Search all notes"),
    ToolIcon(CoreUiFree.BOOKMARK, "Bookmarks")
)

private val TOP_BAR_RIGHT_ICONS = listOf(
    ToolIcon(CoreUiFree.LINK, "Backlinks"),
    ToolIcon(CoreUiFree.LINK_ALT, "Outgoing links"),
    ToolIcon(CoreUiFree.TAGS, "Tags"),
    ToolIcon(CoreUiFree.INBOX, "Archive"),
    ToolIcon(CoreUiFree.LIST, "Outline")
)

/**
 * Assembles the window out of the components in `demo.swing.obsidian`.
 *
 * This is the only place that knows about [NotesStore]: each component is
 * handed the observables and actions it needs, so nothing below depends on how
 * — or where — the state is kept.
 *
 * It is also the root of the UI's lifetime: every panel it builds is
 * `register`ed here, so disposing the window releases the whole tree — the
 * panels themselves watch nothing and never dispose behind your back.
 */
private class ObsidianWindow(private val store: NotesStore) : Bindings by Bindings() {

    // The delegated store properties, as the containers behind them, which is
    // what the two-way bindings take.
    private val activeNote = unwrapBinding(store::activeNote)
    private val activeTool = unwrapBinding(store::activeTool)
    private val leftVisible = unwrapBinding(store::leftVisible)
    private val rightVisible = unwrapBinding(store::rightVisible)

    private val editor = EditorTabs(
        openNotes = store.openNotes,
        activeNote = activeNote,
        // The `+` button and the empty-state link name the file themselves.
        onCreateNote = { store.createUntitledNote() },
        onCloseNote = store::closeNote,
        onFindFile = ::goToFile
    )

    private val sidebar = NotesSidebar(
        sectionTitle = activeTool,
        notes = store.visibleNotes,
        activeNote = activeNote,
        noteCountText = store::countText,
        notesVisible = store::filesToolSelected,
        filterText = unwrapBinding(store::filterText),
        filterVisible = unwrapBinding(store::filterVisible),
        // The sidebar's own button still asks for a name.
        onCreateNote = ::promptForNewNote,
        onOpenNote = store::openNote
    )

    private val rail = IconRail(
        tools = RAIL_TOOLS,
        selectedTool = activeTool,
        footerTools = RAIL_FOOTER_TOOLS,
        onSelect = { leftVisible.value = true }
    )

    private val backlinks = BacklinksPanel(
        linkText = "Visit Flowik Web Site",
        onLinkClicked = { openInBrowser(FLOWIK_URL) }
    )

    private val topBar = TopBar(
        title = store::activeNoteName,
        leftPanelVisible = leftVisible,
        rightPanelVisible = rightVisible,
        leftIcons = TOP_BAR_LEFT_ICONS,
        rightIcons = TOP_BAR_RIGHT_ICONS
    )

    init {
        // Every panel with reactions of its own is a BindingsPanel, and so a
        // Disposable. BacklinksPanel is static, so there is nothing to own.
        listOf(rail, sidebar, editor, topBar).forEach { register(it) }
    }

    fun show(): JFrame {
        // The rail is a sibling of the sidebar, so collapsing the sidebar
        // leaves the rail in place. Each sidebar slides behind its own edge.
        val leftArea = JPanel(BorderLayout()).apply {
            add(rail, BorderLayout.WEST)
            add(register(SlidingPanel(sidebar, SlideSide.LEFT, leftVisible)), BorderLayout.CENTER)
        }
        val main = JPanel(BorderLayout()).apply {
            add(leftArea, BorderLayout.WEST)
            add(editor, BorderLayout.CENTER)
            add(register(SlidingPanel(backlinks, SlideSide.RIGHT, rightVisible)), BorderLayout.EAST)
        }

        val frame = uiFrame("Flowik Vault — Obsidian", width = 1280, height = 800, bindings = this) {
            north { add(topBar) }
            center { add(main) }
        }

        // uiFrame pads its root for form-style layouts; an IDE-like shell wants
        // its panels flush against the window edges.
        (frame.contentPane as JPanel).apply {
            border = null
            (layout as BorderLayout).apply {
                hgap = 0
                vgap = 0
            }
        }
        frame.minimumSize = Dimension(880, 520)
        installShortcuts(frame.rootPane)
        frame.revalidate()
        // The application frame exits the process on close, so this only really
        // matters if the window is closed without the app ending — but it is
        // where the lifetime is decided either way.
        return frame.disposeOnClose(this)
    }

    /** Reveals the filter field and puts the caret in it, even if it was already up. */
    private fun goToFile() {
        store.showFilter()
        sidebar.filterField.focusField()
    }

    private fun promptForNewNote() {
        val name = JOptionPane.showInputDialog(editor, "Note name:", "Untitled") ?: return
        if (name.isBlank()) return

        if (store.createNote(name) == null) {
            JOptionPane.showMessageDialog(
                editor,
                "A note named '${name.trim()}' already exists.",
                "New note",
                JOptionPane.WARNING_MESSAGE
            )
        }
    }

    private fun installShortcuts(rootPane: JRootPane) {
        val menuMask = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx

        fun bind(keyCode: Int, modifiers: Int, name: String, handler: () -> Unit) {
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyCode, modifiers), name)
            rootPane.actionMap.put(name, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) = action { handler() }
            })
        }

        bind(KeyEvent.VK_N, menuMask, "newNote") { store.createUntitledNote() }
        bind(KeyEvent.VK_O, menuMask, "goToFile", ::goToFile)
        bind(KeyEvent.VK_W, menuMask, "closeTab", editor::closeSelectedTab)
        bind(KeyEvent.VK_BACK_SLASH, menuMask, "toggleLeft") { leftVisible.toggle() }
        bind(KeyEvent.VK_BACK_SLASH, menuMask or KeyEvent.SHIFT_DOWN_MASK, "toggleRight") { rightVisible.toggle() }
    }
}

fun obsidianDemo() {
    SwingUtilities.invokeLater {
        FlatLightFlatIJTheme.setup()
        applyObsidianTheme()

        val store = NotesStore()
        listOf("Welcome", "Flowik cheat sheet", "Meeting notes 2026-08-22", "Reading list", "Ideas")
            .forEach { store.createNote(it) }

        // Start from a clean desktop, with one note open, as Obsidian does.
        action {
            store.openNotes.clear()
            store.activeNote = null
            store.notes.items.firstOrNull()?.let { store.openNote(it) }
        }

        ObsidianWindow(store).show()
    }
}

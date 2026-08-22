package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.ObservableList
import flowik.core.action
import flowik.swing.autoRun
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

/**
 * The tabbed working area: one [NoteEditor] per open note, and Obsidian's
 * centred links while nothing is open.
 *
 * Tabs animate in and out: a new one expands into place, and a closed one
 * collapses *before* [onCloseNote] is reported, so the model only changes once
 * the tab has visually gone. Closing therefore has to go through [closeTab] —
 * removing a note from [openNotes] directly still works, it just skips the
 * animation.
 *
 * @param openNotes    the open notes, in tab order — tabs are added, removed and
 *                     reordered to match
 * @param activeNote   two-way: selecting a tab writes it, writing it selects the
 *                     matching tab
 * @param onCreateNote invoked by the `+` button and the empty-state link
 * @param onCloseNote  invoked once a tab has finished collapsing; closing is the
 *                     owner's decision because it also picks the next active note
 * @param onFindFile   invoked by the "Go to file" link
 */
class EditorTabs(
    private val openNotes: ObservableList<ObservableEntity<Note>>,
    private val activeNote: MutableObservable<ObservableEntity<Note>?>,
    private val onCreateNote: () -> Unit,
    private val onCloseNote: (ObservableEntity<Note>) -> Unit,
    private val onFindFile: () -> Unit
) : JPanel(CardLayout()) {

    private val tabs = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        background = BG_SIDEBAR
        border = null
        putClientProperty("JTabbedPane.tabType", "card")
        putClientProperty("JTabbedPane.tabAreaAlignment", "leading")
        putClientProperty("JTabbedPane.showTabSeparators", true)
        putClientProperty("JTabbedPane.tabsPopupPolicy", "asNeeded")
        putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded")
    }

    /** Notes backing the tabs, index-aligned with [tabs]. */
    private val tabNotes = mutableListOf<ObservableEntity<Note>>()

    /** Set while [syncTabs] drives the tabbed pane, to ignore its own events. */
    private var syncing = false

    /** Notes whose tab is collapsing; they are still open until it finishes. */
    private val closing = mutableSetOf<ObservableEntity<Note>>()

    init {
        background = BG_EDITOR
        tabs.putClientProperty(
            "JTabbedPane.trailingComponent",
            // The trailing area spans the rest of the tab strip. BorderLayout
            // pins the button to its left edge, right after the last tab, and
            // stretches it over the full strip height — a FlowLayout would leave
            // it top-aligned at its own smaller height, out of line with the tabs.
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(
                    IconButton(
                        CoreUiFree.PLUS,
                        "New note",
                        padding = Insets(6, 10, 6, 10),
                        onClick = onCreateNote
                    ),
                    BorderLayout.WEST
                )
            }
        )

        tabs.addChangeListener {
            if (syncing) return@addChangeListener
            val index = tabs.selectedIndex
            if (index in tabNotes.indices) action { activeNote.value = tabNotes[index] }
        }

        add(emptyState(), CARD_EMPTY)
        add(tabs, CARD_TABS)

        // Both reactions sit on this container rather than on the tabbed pane:
        // a card switch must never take their component out of the hierarchy,
        // which would dispose them.
        autoRun("editorTabs.sync") { syncTabs() }
        autoRun("editorTabs.card") {
            val card = if (openNotes.size > 0) CARD_TABS else CARD_EMPTY
            (layout as CardLayout).show(this, card)
        }
    }

    /**
     * Collapses the tab of [note] and reports the close once it is gone. A note
     * whose tab is already collapsing is left alone.
     */
    fun closeTab(note: ObservableEntity<Note>) {
        if (!closing.add(note)) return

        val header = headerFor(note)
        if (header == null) {
            // No tab to animate — close straight away.
            closing.remove(note)
            onCloseNote(note)
            return
        }

        header.collapse {
            closing.remove(note)
            onCloseNote(note)
        }
    }

    private fun headerFor(note: ObservableEntity<Note>): NoteTabHeader? {
        val index = tabNotes.indexOfFirst { it === note }
        return if (index >= 0) tabs.getTabComponentAt(index) as? NoteTabHeader else null
    }

    /** Closes the selected tab — what ⌘W is wired to. */
    fun closeSelectedTab() {
        val index = tabs.selectedIndex
        if (index in tabNotes.indices) closeTab(tabNotes[index])
    }

    /** Brings [tabs] in line with [openNotes] and [activeNote]. */
    private fun syncTabs() {
        val open = openNotes.items
        val active = activeNote.value

        syncing = true
        try {
            // Drop the tabs of notes that are no longer open.
            for (index in tabNotes.indices.reversed()) {
                if (open.none { it === tabNotes[index] }) {
                    closing.remove(tabNotes[index])
                    tabs.removeTabAt(index)
                    tabNotes.removeAt(index)
                }
            }

            // Making a collapsing tab active again means it is being reopened:
            // call off the close and let it grow back.
            if (active != null && active in closing) headerFor(active)?.let {
                closing.remove(active)
                it.expand()
            }

            // Insert (or move) the rest so tab order matches openNotes.
            open.forEachIndexed { index, note ->
                if (index < tabNotes.size && tabNotes[index] === note) return@forEachIndexed

                val existing = tabNotes.indexOfFirst { it === note }
                if (existing >= 0) {
                    tabs.removeTabAt(existing)
                    tabNotes.removeAt(existing)
                }
                val editor = NoteEditor(note)
                tabs.insertTab(note[Note::name], null, editor, null, index)
                tabs.setTabComponentAt(index, NoteTabHeader(note) { closeTab(note) })
                tabNotes.add(index, note)

                // A note is opened to be written in — hand it the caret once
                // the new tab has been laid out.
                SwingUtilities.invokeLater { editor.focusEditor() }
            }

            val activeIndex = tabNotes.indexOfFirst { it === active }
            if (activeIndex >= 0 && tabs.selectedIndex != activeIndex) {
                tabs.selectedIndex = activeIndex
            }
        } finally {
            syncing = false
        }
    }

    /** Shown while no note is open — the reference window's centred links. */
    private fun emptyState(): JPanel {
        val links = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(linkLabel("Create new note (${shortcutHint("N")})", onClick = onCreateNote))
            add(Box.createRigidArea(Dimension(0, 22)))
            add(linkLabel("Go to file (${shortcutHint("O")})", onClick = onFindFile))
        }

        // GridBagLayout with a single child centres it in both directions.
        return JPanel(GridBagLayout()).apply {
            background = BG_EDITOR
            add(links)
        }
    }

    private companion object {
        const val CARD_TABS = "tabs"
        const val CARD_EMPTY = "empty"
    }
}

package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.ObservableList
import flowik.core.action
import flowik.swing.autoRun
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * The tabbed working area: one [NoteEditor] per open note, and Obsidian's
 * centred links while nothing is open.
 *
 * @param openNotes    the open notes, in tab order — tabs are added, removed and
 *                     reordered to match
 * @param activeNote   two-way: selecting a tab writes it, writing it selects the
 *                     matching tab
 * @param onCreateNote invoked by the `+` button and the empty-state link
 * @param onCloseNote  invoked by a tab's close button; closing is the owner's
 *                     decision because it also picks the next active note
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

    init {
        background = BG_EDITOR
        tabs.putClientProperty(
            "JTabbedPane.trailingComponent",
            // The trailing area spans the rest of the tab strip, so the button
            // is pinned to its left edge, right after the last tab.
            row(FlowLayout.LEFT, 0, 0, IconButton(CoreUiFree.PLUS, "New note", onClick = onCreateNote))
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

    /** Brings [tabs] in line with [openNotes] and [activeNote]. */
    private fun syncTabs() {
        val open = openNotes.items
        val active = activeNote.value

        syncing = true
        try {
            // Drop the tabs of notes that are no longer open.
            for (index in tabNotes.indices.reversed()) {
                if (open.none { it === tabNotes[index] }) {
                    tabs.removeTabAt(index)
                    tabNotes.removeAt(index)
                }
            }

            // Insert (or move) the rest so tab order matches openNotes.
            open.forEachIndexed { index, note ->
                if (index < tabNotes.size && tabNotes[index] === note) return@forEachIndexed

                val existing = tabNotes.indexOfFirst { it === note }
                if (existing >= 0) {
                    tabs.removeTabAt(existing)
                    tabNotes.removeAt(existing)
                }
                tabs.insertTab(note[Note::name], null, NoteEditor(note), null, index)
                tabs.setTabComponentAt(index, NoteTabHeader(note) { onCloseNote(note) })
                tabNotes.add(index, note)
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

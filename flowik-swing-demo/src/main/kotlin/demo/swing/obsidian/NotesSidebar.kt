package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.core.ObservableEntity
import flowik.core.toggle
import flowik.swing.BindingsPanel
import flowik.swing.text
import flowik.swing.visible
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.function.Supplier
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

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
    vaultName: String = "Flowik Vault",
    preferredWidth: Int = 250
) : BindingsPanel(BorderLayout()) {

    val filterField = register(FilterField(filterText, filterVisible))

    init {
        background = BG_SIDEBAR
        border = MatteBorder(0, 0, 0, 1, LINE)
        preferredSize = Dimension(preferredWidth, 0)

        add(header(noteCountText, filterVisible, onCreateNote), BorderLayout.NORTH)
        add(content(notes, activeNote, onOpenNote), BorderLayout.CENTER)
        add(footer(vaultName), BorderLayout.SOUTH)
    }

    private fun header(
        noteCountText: Supplier<String>,
        filterVisible: MutableObservable<Boolean>,
        onCreateNote: () -> Unit
    ): JPanel {
        val filterButton = IconButton(CoreUiFree.FILTER, "Filter notes by name") { filterVisible.toggle() }
        autoRun("sidebar.filterButton") { filterButton.active = filterVisible.value }

        val actions = row(
            FlowLayout.LEFT, 1, 4,
            IconButton(CoreUiFree.NOTE_ADD, "New note", onClick = onCreateNote),
            IconButton(CoreUiFree.FOLDER, "New folder"),
            IconButton(CoreUiFree.CHEVRON_DOUBLE_UP, "Collapse all"),
            IconButton(CoreUiFree.SORT_ALPHA_DOWN, "Change sort order"),
            filterButton
        )

        val title = caption("", size = 11f, bold = true).apply {
            text { sectionTitle.get().uppercase() }
        }
        val count = caption("", size = 11f).apply {
            text(noteCountText)
            visible(notesVisible)
        }
        val titleRow = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(2, 12, 6, 12)
            add(title, BorderLayout.WEST)
            add(count, BorderLayout.EAST)
        }

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = BG_SIDEBAR
            add(actions)
            add(filterField)
            add(titleRow)
        }
    }

    /** The note list, or a placeholder for the rail tools this demo fakes. */
    private fun content(
        notes: Supplier<List<ObservableEntity<Note>>>,
        activeNote: Supplier<ObservableEntity<Note>?>,
        onOpenNote: (ObservableEntity<Note>) -> Unit
    ): JPanel {
        val cards = JPanel(CardLayout()).apply {
            background = BG_SIDEBAR
            add(register(NoteListView(notes, activeNote, onOpen = onOpenNote)), CARD_NOTES)
            add(placeholder(), CARD_OTHER)
        }
        autoRun("sidebar.section") {
            (cards.layout as CardLayout).show(cards, if (notesVisible.get()) CARD_NOTES else CARD_OTHER)
        }
        return cards
    }

    private fun placeholder() = JPanel(BorderLayout()).apply {
        background = BG_SIDEBAR
        border = EmptyBorder(24, 12, 12, 12)
        add(
            caption("", size = 12f).apply {
                horizontalAlignment = SwingConstants.CENTER
                text { "${sectionTitle.get()} is not part of this demo" }
            },
            BorderLayout.NORTH
        )
    }

    private fun footer(vaultName: String) = JPanel(BorderLayout()).apply {
        background = BG_SIDEBAR
        border = CompoundBorder(MatteBorder(1, 0, 0, 0, LINE), EmptyBorder(2, 6, 2, 6))
        add(
            row(
                FlowLayout.LEFT, 4, 4,
                IconButton(CoreUiFree.SWAP_VERTICAL, "Switch vault", iconSize = 13),
                caption(vaultName, size = 12f, color = TEXT_FG)
            ),
            BorderLayout.WEST
        )
        add(
            row(
                FlowLayout.RIGHT, 1, 4,
                IconButton(CoreUiFree.INFO, "Help", iconSize = 14),
                IconButton(CoreUiFree.SETTINGS, "Settings", iconSize = 14)
            ),
            BorderLayout.EAST
        )
    }

    private companion object {
        const val CARD_NOTES = "notes"
        const val CARD_OTHER = "other"
    }
}

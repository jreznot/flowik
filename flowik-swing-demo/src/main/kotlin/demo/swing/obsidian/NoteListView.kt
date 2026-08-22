package demo.swing.obsidian

import flowik.core.ObservableEntity
import flowik.swing.BindingsPanel
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.AbstractAction
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

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
) : BindingsPanel(CardLayout()) {

    private val model = DefaultListModel<ObservableEntity<Note>>()

    private val list = JList(model).apply {
        cellRenderer = NoteCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        background = BG_SIDEBAR
        border = EmptyBorder(2, 4, 4, 4)
        fixedCellHeight = 26
    }

    init {
        background = BG_SIDEBAR
        add(scrollPane(), CARD_LIST)
        add(placeholder(emptyText), CARD_EMPTY)

        // One reaction owns the whole view: contents follow the notes, the
        // highlight follows the active note, and an empty result swaps the card.
        autoRun("noteList.rebuild") {
            val visible = notes.get()
            // Track each name so a rename repaints the row.
            visible.forEach { it[Note::name] }

            model.clear()
            visible.forEach { model.addElement(it) }

            val active = activeNote.get()
            list.selectedIndex = visible.indexOfFirst { it === active }

            (layout as CardLayout).show(this, if (visible.isEmpty()) CARD_EMPTY else CARD_LIST)
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val index = list.locationToIndex(e.point)
                if (index < 0 || list.getCellBounds(index, index)?.contains(e.point) != true) return
                onOpen(model.getElementAt(index))
            }
        })

        list.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openNote")
        list.actionMap.put("openNote", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                list.selectedValue?.let(onOpen)
            }
        })
    }

    private fun scrollPane() = JScrollPane(list).apply {
        border = null
        background = BG_SIDEBAR
        viewport.background = BG_SIDEBAR
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private fun placeholder(text: String) = JPanel(BorderLayout()).apply {
        background = BG_SIDEBAR
        border = EmptyBorder(28, 12, 12, 12)
        add(
            caption(text, size = 12f).apply { horizontalAlignment = SwingConstants.CENTER },
            BorderLayout.NORTH
        )
    }

    private companion object {
        const val CARD_LIST = "list"
        const val CARD_EMPTY = "empty"
    }
}

/** Renders a note file row: [icon] name */
private class NoteCellRenderer : DefaultListCellRenderer() {
    private val fileIcon = FontIcon.of(CoreUiFree.FILE, 13, ICON_FG)

    override fun getListCellRendererComponent(
        list: JList<*>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        @Suppress("UNCHECKED_CAST")
        val note = value as ObservableEntity<Note>
        super.getListCellRendererComponent(list, note[Note::name], index, isSelected, false)

        icon = fileIcon
        iconTextGap = 7
        border = EmptyBorder(4, 8, 4, 8)
        foreground = TEXT_FG
        background = if (isSelected) SELECTION_BG else BG_SIDEBAR
        return this
    }
}

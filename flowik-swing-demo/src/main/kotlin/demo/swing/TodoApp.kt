package demo.swing

import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme
import flowik.core.*
import flowik.layout.uiFrame
import flowik.swing.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

fun main() {
    todoDemo()
}

data class TodoItem(val text: String, val done: Boolean = false)

/**
 * A JList-based to-do list with keyboard navigation.
 * Arrow keys navigate, Space toggles done, Delete/Backspace removes.
 */
private fun TodoListPanel(
    visibleItems: () -> List<ObservableEntity<TodoItem>>,
    toggleItem: (ObservableEntity<TodoItem>) -> Unit,
    removeItem: (ObservableEntity<TodoItem>) -> Unit
): JScrollPane {
    val listModel = DefaultListModel<ObservableEntity<TodoItem>>()
    val jList = JList(listModel).apply {
        cellRenderer = TodoCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    jList.autoRun("todoList.rebuild") {
        val visible = visibleItems()
        // track each item's done state so the list repaints on toggle
        visible.forEach { it[TodoItem::done] }

        val selectedItem = jList.selectedValue
        listModel.clear()
        visible.forEach { listModel.addElement(it) }

        // Restore selection if the previously selected item is still visible
        val newIndex = visible.indexOf(selectedItem)
        if (newIndex >= 0) {
            jList.selectedIndex = newIndex
        }
    }

    // Space → toggle done state
    jList.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleDone"
    )
    jList.actionMap.put("toggleDone", object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            jList.selectedValue?.let { toggleItem(it) }
        }
    })

    // Delete / Backspace → remove item
    jList.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeItem"
    )
    jList.getInputMap(JComponent.WHEN_FOCUSED).put(
        KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "removeItem"
    )
    jList.actionMap.put("removeItem", object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            val idx = jList.selectedIndex
            val item = jList.selectedValue ?: return
            removeItem(item)
            // Keep selection near the removed position
            SwingUtilities.invokeLater {
                if (listModel.size() > 0) {
                    jList.selectedIndex = idx.coerceAtMost(listModel.size() - 1)
                }
            }
        }
    })

    // Mouse click on the checkbox region → toggle done, on ✕ region → remove
    jList.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            val index = jList.locationToIndex(e.point)
            if (index < 0) return
            val cellBounds = jList.getCellBounds(index, index) ?: return
            val relativeX = e.point.x - cellBounds.x
            // Check if click is in the checkbox area (first ~30px)
            if (relativeX <= 30) {
                toggleItem(listModel.getElementAt(index))
            }
            // Check if click is in the remove "✕" area (last ~30px)
            else if (relativeX >= cellBounds.width - 30) {
                removeItem(listModel.getElementAt(index))
            }
        }
    })

    return JScrollPane(jList)
}

/** Renders a to-do row: [✓] text [✕] */
private class TodoCellRenderer : ListCellRenderer<ObservableEntity<TodoItem>> {
    private val panel = JPanel(BorderLayout(6, 0)).apply {
        border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
    }
    private val checkBox = JCheckBox()
    private val label = JLabel()
    private val removeLabel = JLabel("✕").apply {
        foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
    }

    init {
        panel.add(checkBox, BorderLayout.WEST)
        panel.add(label, BorderLayout.CENTER)
        panel.add(removeLabel, BorderLayout.EAST)
    }

    override fun getListCellRendererComponent(
        list: JList<out ObservableEntity<TodoItem>>,
        value: ObservableEntity<TodoItem>,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val done = value[TodoItem::done]
        checkBox.isSelected = done
        label.text = value[TodoItem::text]

        if (done) {
            label.foreground = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
        } else {
            label.foreground = if (isSelected) list.selectionForeground else list.foreground
        }

        if (isSelected) {
            panel.background = list.selectionBackground
            checkBox.background = list.selectionBackground
        } else {
            panel.background = list.background
            checkBox.background = list.background
        }

        panel.isOpaque = true
        checkBox.isOpaque = true

        return panel
    }
}

fun todoDemo() {
    SwingUtilities.invokeLater {
        FlatLightFlatIJTheme.setup()

        val store = object : Store {
            val todos = observables<TodoItem>()

            var filter by observable("", name = "filter")
            var showCompleted by observable(true, name = "showCompleted")
            var showFilter by observable(false, name = "showFilter")

            val visibleItems by todos.filter { item: ObservableEntity<TodoItem> ->
                val filterText = filter.lowercase()
                (showCompleted || !item[TodoItem::done])
                        && (filterText.isEmpty() || item[TodoItem::text].lowercase().contains(filterText))
            }

            private val doneTodos by todos.filter { it[TodoItem::done] }

            val totalCount by computed { todos.size }
            val doneCount by computed { doneTodos.size }
            val statusText by computed { "$doneCount / $totalCount completed" }

            fun addItem(text: String) = action {
                if (text.isNotBlank()) todos.add(TodoItem(text.trim()))
            }

            fun removeItem(item: ObservableEntity<TodoItem>) = action { todos.remove(item) }

            fun toggleItem(item: ObservableEntity<TodoItem>) = action {
                item[TodoItem::done] = !item[TodoItem::done]
            }

            fun clearCompleted() = action {
                doneTodos.forEach { todos.remove(it) }
            }
        }

        store.addItem("Learn Kotlin reactive programming")
        store.addItem("Build Reaktor component library")
        store.addItem("Write unit tests")

        uiFrame("Todo", width = 540, height = 520) {
            north {
                hbox(gap = 0) {
                    Label("Todo").apply {
                        font = Font("SansSerif", Font.BOLD, 22)
                        border = BorderFactory.createEmptyBorder(4, 0, 8, 0)
                    }
                    hglue()
                    Button("v") {
                        store.showFilter = !store.showFilter
                    }
                }
            }

            center {
                borderPanel(gap = 4) {
                    north {
                        vbox(gap = 4) {
                            Panel(visible = store::showFilter) {
                                hbox(gap = 6) {
                                    Label("Filter:")
                                    TextField(store::filter, columns = 18)
                                    CheckBox(store::showCompleted, "Show completed")
                                }
                            }

                            separator()
                        }
                    }

                    center {
                        add(
                            TodoListPanel(
                                store::visibleItems,
                                store::toggleItem,
                                store::removeItem
                            )
                        )
                    }

                    south {
                        hbox(gap = 6) {
                            Label("New todo:")
                            val input = observable("", name = "newItemInput")
                            val tf = TextField(input, columns = 28)
                            spacer(width = 4)
                            Button("Add") {
                                store.addItem(input.value)
                                input.value = ""
                                tf.requestFocusInWindow()
                            }
                        }
                    }
                }
            }

            south {
                hbox(gap = 6) {
                    Label(store::statusText)
                    hglue()
                    Button("Clear completed") { store.clearCompleted() }
                }
            }
        }
    }
}

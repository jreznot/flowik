package demo

import com.formdev.flatlaf.FlatLightLaf
import reaktor.core.*
import reaktor.layout.rframe
import reaktor.swing.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.KeyEvent
import javax.swing.*

class TodoStore {
    val todos: ObservableObjectList<TodoItem> = ObservableObjectList()
    val filter: ObservableValue<String> = observable("", name = "filter")
    val showCompleted: ObservableValue<Boolean> = observable(true, name = "showCompleted")
    val showFilter: ObservableValue<Boolean> = observable(false, name = "showFilter")

    val visibleItems: Derived<List<ObservableObject<TodoItem>>> = derived {
        val filterText = filter.value.lowercase()
        todos.items.filter { item ->
            (showCompleted.value || !item[TodoItem::done].value)
                    && (filterText.isEmpty() || item[TodoItem::text].value.lowercase().contains(filterText))
        }
    }

    val totalCount: Derived<Int> = derived { todos.size }
    val doneCount: Derived<Int> = derived { todos.items.count { it[TodoItem::done].value } }

    val statusText: Derived<String> = derived {
        "${doneCount.value} / ${totalCount.value} completed"
    }

    fun addItem(text: String) = action {
        if (text.isNotBlank()) {
            todos.add(TodoItem(text.trim()))
        }
    }

    fun removeItem(item: ObservableObject<TodoItem>) = action {
        todos.remove(item)
    }

    fun toggleItem(item: ObservableObject<TodoItem>) = action {
        item[TodoItem::done].value = !item[TodoItem::done].value
    }

    fun clearCompleted() = action {
        val completed = todos.items.filter { it[TodoItem::done].value }
        completed.forEach { todos.remove(it) }
    }
}

data class TodoItem(val text: String, val done: Boolean = false)

/**
 * A JList-based to-do list with keyboard navigation.
 * Arrow keys navigate, Space toggles done, Delete/Backspace removes.
 */
private fun todoListPanel(store: TodoStore): JScrollPane {
    val listModel = DefaultListModel<ObservableObject<TodoItem>>()
    val jList = JList<ObservableObject<TodoItem>>(listModel).apply {
        cellRenderer = TodoCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    reaction("todoList.rebuild") {
        val visible = store.visibleItems.value
        // track each item's done state so the list repaints on toggle
        visible.forEach { it[TodoItem::done].value }

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
        override fun actionPerformed(e: java.awt.event.ActionEvent?) {
            jList.selectedValue?.let { store.toggleItem(it) }
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
        override fun actionPerformed(e: java.awt.event.ActionEvent?) {
            val idx = jList.selectedIndex
            val item = jList.selectedValue ?: return
            store.removeItem(item)
            // Keep selection near the removed position
            SwingUtilities.invokeLater {
                if (listModel.size() > 0) {
                    jList.selectedIndex = idx.coerceAtMost(listModel.size() - 1)
                }
            }
        }
    })

    // Mouse click on the checkbox region → toggle done, on ✕ region → remove
    jList.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent) {
            val index = jList.locationToIndex(e.point)
            if (index < 0) return
            val cellBounds = jList.getCellBounds(index, index) ?: return
            val relativeX = e.point.x - cellBounds.x
            // Check if click is in the checkbox area (first ~30px)
            if (relativeX <= 30) {
                store.toggleItem(listModel.getElementAt(index))
            }
            // Check if click is in the remove "✕" area (last ~30px)
            else if (relativeX >= cellBounds.width - 30) {
                store.removeItem(listModel.getElementAt(index))
            }
        }
    })

    return JScrollPane(jList)
}

/** Renders a to-do row: [✓] text [✕] */
private class TodoCellRenderer : ListCellRenderer<ObservableObject<TodoItem>> {
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
        list: JList<out ObservableObject<TodoItem>>,
        value: ObservableObject<TodoItem>,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val done = value[TodoItem::done].value
        checkBox.isSelected = done
        label.text = value[TodoItem::text].value

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

fun main() {
    SwingUtilities.invokeLater {
        FlatLightLaf.setup()

        val store = TodoStore()

        store.addItem("Learn Kotlin reactive programming")
        store.addItem("Build Reaktor component library")
        store.addItem("Write unit tests")

        rframe("Todo", width = 540, height = 520) {
            north {
                rhbox(gap = 0) {
                    Label("Todo").apply {
                        font = Font("SansSerif", Font.BOLD, 22)
                        border = BorderFactory.createEmptyBorder(4, 0, 8, 0)
                    }
                    hglue()
                    this.Button("v") {
                        store.showFilter.value = !store.showFilter.value
                    }
                }
            }

            center {
                rborderPanel(gap = 4) {
                    north {
                        rvbox(gap = 4) {
                            rpanel(visible = store.showFilter) {
                                rhbox(gap = 6) {
                                    Label("Filter:")
                                    TextField(store.filter, columns = 18)
                                    CheckBox(store.showCompleted, "Show completed")
                                }
                            }

                            separator()
                        }
                    }

                    center {
                        add(todoListPanel(store))
                    }

                    // Add-item row at the bottom
                    south {
                        rhbox(gap = 6) {
                            Label("New todo:")
                            val input = observable("", name = "newItemInput")
                            val tf = TextField(input, columns = 28)
                            spacer(width = 4)
                            this.Button("Add") {
                                store.addItem(input.value)
                                input.value = ""
                                tf.requestFocusInWindow()
                            }
                        }
                    }
                }
            }

            south {
                rhbox(gap = 6) {
                    Label(store.statusText)
                    hglue()
                    this.Button("Clear completed") { store.clearCompleted() }
                }
            }
        }
    }
}

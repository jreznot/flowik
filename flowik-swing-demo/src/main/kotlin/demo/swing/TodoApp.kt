package demo.swing

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme
import flowik.core.*
import flowik.layout.PanelScope
import flowik.layout.uiFrame
import flowik.swing.*
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.function.Supplier
import javax.swing.*
import javax.swing.border.EmptyBorder

fun main() {
    todoDemo()
}

data class TodoItem(val text: String, val done: Boolean = false)

/**
 * The colours the CoreUI glyphs are drawn in. Getters rather than values, so
 * they follow the look and feel installed in [todoDemo] instead of whatever was
 * in place when this file's classes happened to load.
 */
private val TEXT_FG: Color get() = UIManager.getColor("Label.foreground") ?: Color.DARK_GRAY
private val MUTED_FG: Color get() = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
private val ACCENT: Color get() = UIManager.getColor("Component.accentColor") ?: Color(0x26, 0x75, 0xBF)
private val DANGER: Color get() = Color(0xC7, 0x4A, 0x4A)

/** A CoreUI glyph as a Swing [Icon]. */
private fun coreUi(ikon: Ikon, size: Int = 14, color: Color = TEXT_FG): FontIcon =
    FontIcon.of(ikon, size, color)

/** Row geometry. The hot zones and the renderer's icons are sized from these. */
private const val ROW_HEIGHT = 28
private const val ROW_PADDING = 8
private const val ICON_SIZE = 16
private const val REMOVE_ICON_SIZE = 13
private const val EMPTY_ICON_SIZE = 32

/** Width of the leading toggle hot zone, measured from the row's left edge. */
private const val TOGGLE_ZONE = 30

/** Width of the trailing remove hot zone, measured from the row's right edge. */
private const val REMOVE_ZONE = 30

/** The parts of a row the mouse can be over — the first two are click targets. */
private enum class RowZone { TOGGLE, TEXT, REMOVE }

/** A row index paired with the zone a point fell in. */
private data class RowHit(val index: Int, val zone: RowZone)

/**
 * Where the pointer is inside the list, shared between [TodoList] and its
 * renderer: the renderer lights up the icon under the mouse, and only the row
 * under the mouse shows its remove button at all.
 */
private class RowHover {
    var hit: RowHit? = null
}

/**
 * A borderless, icon-only button, drawn in the accent colour while [active].
 *
 * The reaction that recolours the glyph belongs to the [PanelScope] that builds
 * the button, so it is released with the rest of the window's bindings.
 */
private fun PanelScope.IconButton(
    ikon: Ikon,
    tooltip: String,
    size: Int = 16,
    active: () -> Boolean = { false },
    onClick: () -> Unit
): JButton = Button("", onClick).apply {
    toolTipText = tooltip
    isFocusable = false
    putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
    autoRun("iconButton.$tooltip") {
        icon = coreUi(ikon, size, if (active()) ACCENT else MUTED_FG)
    }
}

/**
 * An ordinary button with a leading CoreUI glyph.
 *
 * Swing only greys out generated disabled icons, which a font glyph is not, so
 * the muted variant is supplied explicitly — these buttons do get disabled.
 */
private fun PanelScope.IconTextButton(
    label: String,
    ikon: Ikon,
    onClick: () -> Unit
): JButton = Button(label, onClick).apply {
    icon = coreUi(ikon, 14, UIManager.getColor("Button.foreground") ?: TEXT_FG)
    disabledIcon = coreUi(ikon, 14, MUTED_FG)
    iconTextGap = 6
}

/**
 * The rows: a [JList] that knows about the two icon hot zones in each row, and
 * paints a centred empty state when it has nothing to show.
 *
 * Plain Swing on purpose — nothing here reads an observable. The model is filled
 * by [TodoListPanel], which keeps the reactive part in one place and leaves this
 * class usable with a hand-filled model in a test.
 */
private class TodoList(
    private val listModel: DefaultListModel<ObservableEntity<TodoItem>>,
    private val emptyMessage: Supplier<String>,
    private val onToggle: (ObservableEntity<TodoItem>) -> Unit,
    private val onRemove: (ObservableEntity<TodoItem>) -> Unit
) : JList<ObservableEntity<TodoItem>>(listModel) {

    private val hover = RowHover()

    init {
        cellRenderer = TodoCellRenderer(hover)
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        fixedCellHeight = ROW_HEIGHT
        // getToolTipText alone is not enough: a component only gets tooltips
        // once the shared manager knows about it.
        ToolTipManager.sharedInstance().registerComponent(this)
        installKeyBindings()
        installMouseHandling()
    }

    /** Space toggles the selected row, Delete or Backspace removes it. */
    private fun installKeyBindings() {
        val inputMap = getInputMap(JComponent.WHEN_FOCUSED)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), ACTION_TOGGLE)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), ACTION_REMOVE)
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), ACTION_REMOVE)

        actionMap.put(ACTION_TOGGLE, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                selectedValue?.let(onToggle)
            }
        })
        actionMap.put(ACTION_REMOVE, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                val index = selectedIndex
                val item = selectedValue ?: return
                onRemove(item)
                // The rebuild has already run; put the selection back where the
                // removed row was.
                SwingUtilities.invokeLater {
                    if (listModel.size() > 0) selectedIndex = index.coerceAtMost(listModel.size() - 1)
                }
            }
        })
    }

    private fun installMouseHandling() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val hit = rowAt(e.point) ?: return
                val item = listModel.getElementAt(hit.index)
                when (hit.zone) {
                    RowZone.TOGGLE -> onToggle(item)
                    RowZone.REMOVE -> onRemove(item)
                    // Double-clicking the text means the same as hitting the circle.
                    RowZone.TEXT -> if (e.clickCount == 2) onToggle(item)
                }
            }

            override fun mouseExited(e: MouseEvent) = updateHover(null)
        })

        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) = updateHover(rowAt(e.point))
        })
    }

    /** Which row and zone [point] is on, or `null` past the last row. */
    private fun rowAt(point: Point): RowHit? {
        val index = locationToIndex(point)
        if (index < 0) return null
        // locationToIndex returns the nearest row, so the empty space below the
        // last one has to be rejected by hand.
        val bounds = getCellBounds(index, index) ?: return null
        if (!bounds.contains(point)) return null

        val x = point.x - bounds.x
        val zone = when {
            x < TOGGLE_ZONE -> RowZone.TOGGLE
            x > bounds.width - REMOVE_ZONE -> RowZone.REMOVE
            else -> RowZone.TEXT
        }
        return RowHit(index, zone)
    }

    private fun updateHover(hit: RowHit?) {
        if (hover.hit == hit) return
        val previous = hover.hit?.index ?: -1
        hover.hit = hit
        cursor = Cursor.getPredefinedCursor(
            if (hit != null && hit.zone != RowZone.TEXT) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR
        )
        repaintRow(previous)
        repaintRow(hit?.index ?: -1)
    }

    private fun repaintRow(index: Int) {
        if (index < 0 || index >= listModel.size()) return
        getCellBounds(index, index)?.let { repaint(it) }
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val hit = rowAt(event.point) ?: return null
        return when (hit.zone) {
            RowZone.TOGGLE -> "Toggle done — or press Space"
            RowZone.REMOVE -> "Remove — or press Delete"
            RowZone.TEXT -> listModel.getElementAt(hit.index)[TodoItem::text]
        }
    }

    /**
     * Fill the viewport while empty, so the empty state has the whole area to
     * centre itself in. Tracking the viewport with rows in the model would pin
     * the list to the visible height and stop it scrolling.
     */
    override fun getScrollableTracksViewportHeight(): Boolean = listModel.size() == 0

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (listModel.size() == 0) paintEmptyState(g)
    }

    /** A big muted glyph over one line of text, where the rows would be. */
    private fun paintEmptyState(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val glyph = coreUi(CoreUiFree.TASK, EMPTY_ICON_SIZE, MUTED_FG)
            glyph.paintIcon(this, g2, (width - glyph.iconWidth) / 2, height / 2 - glyph.iconHeight)

            val message = emptyMessage.get()
            g2.color = MUTED_FG
            g2.font = font.deriveFont(Font.PLAIN, 13f)
            val metrics = g2.fontMetrics
            g2.drawString(message, (width - metrics.stringWidth(message)) / 2, height / 2 + metrics.ascent)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val ACTION_TOGGLE = "toggleDone"
        const val ACTION_REMOVE = "removeItem"
    }
}

/** Renders a to-do row: a done circle, the text, and a remove ✕ on hover. */
private class TodoCellRenderer(
    private val hover: RowHover
) : ListCellRenderer<ObservableEntity<TodoItem>> {

    // One glyph per state, built once: a renderer runs on every repaint of
    // every row.
    private val checkedIcon = coreUi(CoreUiFree.CHECK_CIRCLE, ICON_SIZE, ACCENT)
    private val uncheckedIcon = coreUi(CoreUiFree.CIRCLE, ICON_SIZE, MUTED_FG)
    private val uncheckedHoverIcon = coreUi(CoreUiFree.CHECK_CIRCLE, ICON_SIZE, MUTED_FG)
    private val removeIcon = coreUi(CoreUiFree.X, REMOVE_ICON_SIZE, MUTED_FG)
    private val removeHoverIcon = coreUi(CoreUiFree.X, REMOVE_ICON_SIZE, DANGER)

    private val toggle = iconSlot(TOGGLE_ZONE - ROW_PADDING)
    private val label = JLabel()
    private val remove = iconSlot(REMOVE_ZONE - ROW_PADDING)

    private val panel = JPanel(BorderLayout()).apply {
        border = EmptyBorder(0, ROW_PADDING, 0, ROW_PADDING)
        isOpaque = true
        add(toggle, BorderLayout.WEST)
        add(label, BorderLayout.CENTER)
        add(remove, BorderLayout.EAST)
    }

    /** A fixed-width holder, so icon columns line up with the hot zones. */
    private fun iconSlot(width: Int) = JLabel().apply {
        horizontalAlignment = SwingConstants.CENTER
        preferredSize = Dimension(width, ICON_SIZE)
    }

    override fun getListCellRendererComponent(
        list: JList<out ObservableEntity<TodoItem>>,
        value: ObservableEntity<TodoItem>,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val done = value[TodoItem::done]
        val hit = hover.hit?.takeIf { it.index == index }

        toggle.icon = when {
            done -> checkedIcon
            // Hovering an open item previews the check it is about to get.
            hit?.zone == RowZone.TOGGLE -> uncheckedHoverIcon
            else -> uncheckedIcon
        }

        val text = value[TodoItem::text]
        label.text = if (done) "<html><s>${escapeHtml(text)}</s></html>" else text
        label.foreground = when {
            done -> MUTED_FG
            isSelected -> list.selectionForeground
            else -> list.foreground
        }

        // The ✕ shows up only on the row under the mouse, so a list at rest is
        // just text and circles.
        remove.icon = when {
            hit == null -> null
            hit.zone == RowZone.REMOVE -> removeHoverIcon
            else -> removeIcon
        }

        panel.background = if (isSelected) list.selectionBackground else list.background
        return panel
    }

    /** Done rows render as HTML to get the strikethrough, and text is input. */
    private fun escapeHtml(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

/**
 * A scrollable to-do list with keyboard navigation and clickable row icons.
 *
 * The reaction that fills the list goes into the [Bindings] group of whoever
 * builds the panel — inside `uiFrame` that is the frame's group, supplied by the
 * builder scope.
 */
context(bindings: Bindings)
private fun TodoListPanel(
    visibleItems: () -> List<ObservableEntity<TodoItem>>,
    emptyMessage: Supplier<String>,
    toggleItem: (ObservableEntity<TodoItem>) -> Unit,
    removeItem: (ObservableEntity<TodoItem>) -> Unit
): JScrollPane {
    val listModel = DefaultListModel<ObservableEntity<TodoItem>>()
    val jList = TodoList(listModel, emptyMessage, toggleItem, removeItem)

    bindings.autoRun("todoList.rebuild") {
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

    return JScrollPane(jList).apply {
        // A short list would otherwise leave the panel colour showing under the
        // last row instead of the list's own background.
        viewport.background = jList.background
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
            val statusText by computed {
                if (totalCount == 0) "Nothing on the list" else "$doneCount of $totalCount completed"
            }

            /** Why the list is empty — a filter that matches nothing reads differently. */
            val emptyMessage by computed {
                if (totalCount == 0) "Nothing to do — add a to-do below" else "No to-do matches the filter"
            }

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

        // The UI's reactions live here, and are released when the window is
        // closed — no component ever disposes anything behind your back.
        val bindings = Bindings()

        uiFrame("Todo", width = 560, height = 540, bindings = bindings) {
            north {
                hbox(gap = 0) {
                    Label("Todo").apply {
                        font = Font("SansSerif", Font.BOLD, 22)
                        icon = coreUi(CoreUiFree.TASK, 22, ACCENT)
                        iconTextGap = 10
                        border = BorderFactory.createEmptyBorder(4, 0, 8, 0)
                    }
                    hglue()
                    IconButton(
                        CoreUiFree.FILTER,
                        "Show the filter row",
                        active = store::showFilter
                    ) {
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
                                    TextField(store::filter.asObservable(), columns = 18).apply {
                                        putClientProperty(
                                            FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                                            coreUi(CoreUiFree.MAGNIFYING_GLASS, 13, MUTED_FG)
                                        )
                                        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filter by name")
                                        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true)
                                    }
                                    CheckBox(store::showCompleted.asObservable(), "Show completed")
                                    hglue()
                                }
                            }

                            separator()
                        }
                    }

                    center {
                        add(
                            TodoListPanel(
                                store::visibleItems,
                                store::emptyMessage,
                                store::toggleItem,
                                store::removeItem
                            )
                        )
                    }

                    south {
                        hbox(gap = 6) {
                            val input = observable("", name = "newItemInput")
                            val tf = TextField(input, columns = 28).apply {
                                putClientProperty(
                                    FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                                    coreUi(CoreUiFree.PENCIL, 13, MUTED_FG)
                                )
                                putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "What needs to be done?")
                            }

                            // One batch per submission, whether it came from the
                            // Return key or from the button.
                            fun submit() = action {
                                store.addItem(input.value)
                                input.value = ""
                                tf.requestFocusInWindow()
                            }

                            tf.addActionListener { submit() }
                            spacer(width = 4)
                            IconTextButton("Add", CoreUiFree.PLUS) { submit() }
                                .enabled { input.value.isNotBlank() }
                        }
                    }
                }
            }

            south {
                hbox(gap = 6) {
                    Label(store::statusText).apply {
                        icon = coreUi(CoreUiFree.CHECK_ALT, 13, MUTED_FG)
                        iconTextGap = 6
                        foreground = MUTED_FG
                    }
                    hglue()
                    IconTextButton("Clear completed", CoreUiFree.TRASH) { store.clearCompleted() }
                        .enabled { store.doneCount > 0 }
                }
            }
        }.disposeOnClose(bindings)
    }
}

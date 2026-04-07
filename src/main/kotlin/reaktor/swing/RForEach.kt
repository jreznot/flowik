package reaktor.swing

import reaktor.core.ListChange
import reaktor.core.ObservableList
import reaktor.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A reactive JPanel whose children are kept in sync with an [ObservableList]
 * via a mapping function.
 *
 * Children are created, inserted, replaced, and removed using fine-grained
 * [ListChange] events — no full rebuild occurs on incremental mutations.
 *
 * @param list   The observable list to mirror as children.
 * @param layout Panel layout manager; defaults to vertical [BoxLayout].
 * @param map    Produces a [JComponent] for each list item.
 */
class RForEach<T>(
    list: ObservableList<T>,
    layout: LayoutManager? = null,
    private val map: (T) -> JComponent
) : JPanel(), RComponent {

    /** Parallel list of child components, kept in sync with [list]. */
    private val children = mutableListOf<JComponent>()

    /**
     * Set to true in [removeNotify] so that any in-flight [ObservableList.onChange]
     * callbacks are ignored after the panel is removed from the hierarchy.
     */
    private var disposed = false

    init {
        this.layout = layout ?: BoxLayout(this, BoxLayout.Y_AXIS)

        // Bootstrap from current contents
        for (item in list.items) {
            val comp = map(item)
            children.add(comp)
            add(comp)
        }

        // Fine-grained incremental updates
        list.onChange { change ->
            if (disposed) return@onChange
            when (change) {
                is ListChange.Insert -> {
                    val comp = map(change.item)
                    children.add(change.index, comp)
                    add(comp, change.index)
                    revalidate(); repaint()
                }
                is ListChange.Remove -> {
                    val comp = children.removeAt(change.index)
                    remove(comp)
                    revalidate(); repaint()
                }
                is ListChange.Update -> {
                    val old = children[change.index]
                    remove(old)
                    val new = map(change.new)
                    children[change.index] = new
                    add(new, change.index)
                    revalidate(); repaint()
                }
                is ListChange.Clear -> {
                    children.clear()
                    removeAll()
                    revalidate(); repaint()
                }
            }
        }
    }

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
        disposed = true
    }
}

/**
 * DSL builder — adds an [RForEach] panel to the receiver scope.
 *
 * Example:
 * ```kotlin
 * rforEach(store.items) { item ->
 *     RLabel.of(item.name)
 * }
 * ```
 */
fun <T> PanelScope.ForEach(
    list: ObservableList<T>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): RForEach<T> = RForEach(list, layout, map).also { panel.add(it) }

package flowik.swing

import flowik.core.Computed
import flowik.core.ListChange
import flowik.core.ObservableList
import flowik.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A reactive JPanel whose children are kept in sync with an [ObservableList]
 * or a [Computed]<List<T>> via a mapping function.
 *
 * Call [bindItems] to connect the list and supply the mapping function.
 *
 * - The [ObservableList] overload uses fine-grained [ListChange] events —
 *   no full rebuild occurs on incremental mutations.
 * - The [Computed] overload uses [autoReaction] to fully rebuild all children
 *   whenever the computed list changes.
 */
class RForEach<T>(layout: LayoutManager? = null) : JPanel(), RComponent {

    private val children = mutableListOf<JComponent>()
    private var disposed = false

    init {
        this.layout = layout ?: BoxLayout(this, BoxLayout.Y_AXIS)
    }

    fun bindItems(list: ObservableList<T>, map: (T) -> JComponent) {
        for (item in list.items) {
            val comp = map(item)
            children.add(comp)
            add(comp)
        }
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

    /**
     * Bind a [Computed]<List<T>> to this panel. An [autoReaction] is set up
     * that reads [computedList]`.value` (tracking its dependencies) and
     * rebuilds **all** children via [map] whenever the list changes.
     *
     * The reaction is automatically disposed when the component is removed
     * from the Swing hierarchy (via [removeNotify]).
     */
    fun bindItems(computedList: Computed<List<T>>, map: (T) -> JComponent) {
        autoReaction("RForEach.bindItems(Computed<List<T>>)") {
            // Clear existing children
            children.clear()
            removeAll()

            // Rebuild from the current computed snapshot
            for (item in computedList.value) {
                val comp = map(item)
                children.add(comp)
                add(comp)
            }

            revalidate()
            repaint()
        }
    }

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
        disposed = true
    }
}

fun <T> PanelScope.ForEach(
    list: ObservableList<T>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): RForEach<T> = RForEach<T>(layout).also { it.bindItems(list, map); panel.add(it) }

fun <T> PanelScope.ForEach(
    computedList: Computed<List<T>>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): RForEach<T> = RForEach<T>(layout).also { it.bindItems(computedList, map); panel.add(it) }

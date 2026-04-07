package reaktor.swing

import reaktor.core.ListChange
import reaktor.core.ObservableItems
import reaktor.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A reactive JPanel whose children are kept in sync with an [ObservableItems]
 * via a mapping function.
 *
 * Call [bindItems] to connect the list and supply the mapping function.
 * Children are created, inserted, replaced, and removed using fine-grained
 * [ListChange] events — no full rebuild occurs on incremental mutations.
 */
class RForEach<T>(layout: LayoutManager? = null) : JPanel(), RComponent {

    private val children = mutableListOf<JComponent>()
    private var disposed = false

    init {
        this.layout = layout ?: BoxLayout(this, BoxLayout.Y_AXIS)
    }

    fun bindItems(list: ObservableItems<T>, map: (T) -> JComponent) {
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

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
        disposed = true
    }
}

fun <T> PanelScope.ForEach(
    list: ObservableItems<T>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): RForEach<T> = RForEach<T>(layout).also { it.bindItems(list, map); panel.add(it) }

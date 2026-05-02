package flowik.swing

import flowik.core.Computed
import flowik.core.ListChange
import flowik.core.ObservableList
import flowik.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

fun <T> JPanel.bindItems(list: ObservableList<T>, map: (T) -> JComponent) {
    val children = mutableListOf<JComponent>()
    for (item in list.items) {
        val comp = map(item)
        children.add(comp)
        add(comp)
    }

    val subscription = list.onChange { change ->
        when (change) {
            is ListChange.Insert -> {
                val comp = map(change.item)
                children.add(change.index, comp)
                add(comp, change.index)
                revalidate()
                repaint()
            }
            is ListChange.Remove -> {
                val comp = children.removeAt(change.index)
                remove(comp)
                revalidate()
                repaint()
            }
            is ListChange.Update -> {
                val old = children[change.index]
                remove(old)
                val new = map(change.new)
                children[change.index] = new
                add(new, change.index)
                revalidate()
                repaint()
            }
            is ListChange.Clear -> {
                children.clear()
                removeAll()
                revalidate()
                repaint()
            }
        }
    }

    onDetached { subscription.dispose() }
}

fun <T> JPanel.bindItems(computedList: Computed<List<T>>, map: (T) -> JComponent) {
    val children = mutableListOf<JComponent>()
    autoRun("JForEach.items") {
        children.clear()
        removeAll()
        for (item in computedList.value) {
            val comp = map(item)
            children.add(comp)
            add(comp)
        }
        revalidate()
        repaint()
    }
}

fun <T> PanelScope.ForEach(
    list: ObservableList<T>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): JPanel = JPanel().also {
    it.layout = layout ?: BoxLayout(it, BoxLayout.Y_AXIS)
    it.bindItems(list, map)
    panel.add(it)
}

fun <T> PanelScope.ForEach(
    computedList: Computed<List<T>>,
    layout: LayoutManager? = null,
    map: (T) -> JComponent
): JPanel = JPanel().also {
    it.layout = layout ?: BoxLayout(it, BoxLayout.Y_AXIS)
    it.bindItems(computedList, map)
    panel.add(it)
}

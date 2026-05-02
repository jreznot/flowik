package flowik.swing

import flowik.core.Computed
import flowik.core.ListChange
import flowik.core.ObservableList
import flowik.layout.PanelScope
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.JScrollPane

private class ReactiveListModel<T>(
    private val data: ObservableList<T>
) : AbstractListModel<T>() {
    private var snapshot = data.items.toList()
    private val reaction: flowik.core.Reaction

    init {
        data.onChange { change ->
            when (change) {
                is ListChange.Insert -> {
                    snapshot = data.items
                    fireIntervalAdded(this, change.index, change.index)
                }
                is ListChange.Remove -> {
                    snapshot = data.items
                    fireIntervalRemoved(this, change.index, change.index)
                }
                is ListChange.Update -> {
                    snapshot = data.items
                    fireContentsChanged(this, change.index, change.index)
                }
                is ListChange.Clear -> {
                    val oldSize = snapshot.size
                    snapshot = emptyList()
                    if (oldSize > 0) fireIntervalRemoved(this, 0, oldSize - 1)
                }
            }
        }
        reaction = flowik.core.reaction("ReactiveListModel.sync") {
            val newSnapshot = data.items
            if (newSnapshot != snapshot) {
                snapshot = newSnapshot
                fireContentsChanged(this, 0, maxOf(0, snapshot.size - 1))
            }
        }
    }

    override fun getSize(): Int = snapshot.size

    override fun getElementAt(index: Int): T = snapshot[index]

    fun dispose() {
        reaction.dispose()
    }
}

class ScrollableListBox<T> : JScrollPane() {
    internal val jList = JList<T>()
    init {
        setViewportView(jList)
    }
}

fun <T> JList<T>.bindItems(source: ObservableList<T>) {
    val currentModel = model as? ReactiveListModel<*>
    currentModel?.dispose()

    val newModel = ReactiveListModel(source)
    this.model = newModel

    onDetached { newModel.dispose() }
}

fun <T> JList<T>.bindItems(computed: Computed<List<T>>) {
    val data = ObservableList<T>()
    bindItems(data)
    autoReaction("JList.computed") {
        data.setAll(computed.value)
    }
}

fun <T> PanelScope.ListBox(data: ObservableList<T>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        it.jList.bindItems(data)
        panel.add(it)
    }
}

fun <T> PanelScope.ListBox(computed: Computed<List<T>>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        it.jList.bindItems(computed)
        panel.add(it)
    }
}

package flowik.swing

import flowik.core.*
import flowik.layout.PanelScope
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.JScrollPane

private class ReactiveListModel<T>(
    bindings: Bindings,
    private val data: ObservableList<T>
) : AbstractListModel<T>() {
    private var snapshot = data.items.toList()

    init {
        bindings.register(data.onChange { change ->
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
        })
        bindings.autoRun("ReactiveListModel.sync") {
            val newSnapshot = data.items
            if (newSnapshot != snapshot) {
                snapshot = newSnapshot
                fireContentsChanged(this, 0, maxOf(0, snapshot.size - 1))
            }
        }
    }

    override fun getSize(): Int = snapshot.size

    override fun getElementAt(index: Int): T = snapshot[index]
}

class ScrollableListBox<T> : JScrollPane() {
    internal val jList = JList<T>()
    init {
        setViewportView(jList)
    }
}

context(bindings: Bindings)
fun <T> JList<T>.items(source: ObservableList<T>) {
    model = ReactiveListModel(bindings, source)
}

context(bindings: Bindings)
fun <T> JList<T>.items(computed: ReadableObservable<List<T>>) {
    val data = ObservableList<T>()
    items(data)
    bindings.autoRun("JList.computed") {
        data.setAll(computed.value)
    }
}

fun <T> PanelScope.ListBox(data: ObservableList<T>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        it.jList.items(data)
        panel.add(it)
    }
}

fun <T> PanelScope.ListBox(computed: ReadableObservable<List<T>>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        it.jList.items(computed)
        panel.add(it)
    }
}

fun <T> PanelScope.ListBox(computed: MutableObservable<List<T>>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        // Read-only: the property behind a list is usually a Computed.
        it.jList.items(computed)
        panel.add(it)
    }
}

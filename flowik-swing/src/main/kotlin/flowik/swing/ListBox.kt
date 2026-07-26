package flowik.swing

import flowik.core.ReadableObservable
import flowik.core.Disposable
import flowik.core.ListChange
import flowik.core.ObservableList
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import javax.swing.AbstractListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JScrollPane
import kotlin.reflect.KProperty0

private class ReactiveListModel<T>(
    component: JComponent,
    private val data: ObservableList<T>
) : AbstractListModel<T>() {
    private var snapshot = data.items.toList()
    private val subscription: Disposable

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
        subscription = component.autoRun("ReactiveListModel.sync") {
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
        subscription.dispose()
    }
}

class ScrollableListBox<T> : JScrollPane() {
    internal val jList = JList<T>()
    init {
        setViewportView(jList)
    }
}

fun <T> JList<T>.items(source: ObservableList<T>) {
    val currentModel = model as? ReactiveListModel<*>
    currentModel?.dispose()

    val newModel = ReactiveListModel(this, source)
    this.model = newModel

    onDetached { newModel.dispose() }
}

fun <T> JList<T>.items(computed: ReadableObservable<List<T>>) {
    val data = ObservableList<T>()
    items(data)
    autoRun("JList.computed") {
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

fun <T> PanelScope.ListBox(computed: KProperty0<List<T>>): ScrollableListBox<T> {
    return ScrollableListBox<T>().also {
        it.jList.items(unwrapBinding(computed))
        panel.add(it)
    }
}

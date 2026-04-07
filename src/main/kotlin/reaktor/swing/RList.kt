package reaktor.swing

import reaktor.core.Derived
import reaktor.core.ListChange
import reaktor.core.ObservableItems
import reaktor.layout.PanelScope
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.JScrollPane

/**
 * A reactive list view.
 *
 * Call [bindItems] to connect an [ObservableItems] or [Derived]<[List]>.
 * Fine-grained [ListChange] events are used when binding an [ObservableItems]
 * directly; a [Derived] source rebuilds the list on each change.
 */
class RList<T> : JScrollPane(), RComponent {

    val jList = JList<T>()
    private var listModel: ReactiveListModel<T>? = null

    init { setViewportView(jList) }

    /** Bind directly to an [ObservableItems]; uses fine-grained change events. */
    fun bindItems(source: ObservableItems<T>) {
        listModel?.dispose()
        val model = ReactiveListModel(source)
        listModel = model
        jList.model = model
    }

    /** Bind to a computed list; rebuilds on every change. */
    fun bindItems(computed: Derived<List<T>>) {
        val data = ObservableItems<T>()
        bindItems(data)
        autoReaction("RList.computed") { data.setAll(computed.value) }
    }

    override fun removeNotify() {
        super<JScrollPane>.removeNotify()
        listModel?.dispose()
        super<RComponent>.removeNotify()
    }

    private class ReactiveListModel<T>(
        private val data: ObservableItems<T>
    ) : AbstractListModel<T>() {

        private var snapshot = data.items.toList()
        private val reaction: reaktor.core.Reaction

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
            reaction = reaktor.core.reaction("RListModel.sync") {
                val newSnapshot = data.items
                if (newSnapshot != snapshot) {
                    snapshot = newSnapshot
                    fireContentsChanged(this, 0, maxOf(0, snapshot.size - 1))
                }
            }
        }

        override fun getSize(): Int = snapshot.size
        override fun getElementAt(index: Int): T = snapshot[index]

        fun dispose() { reaction.dispose() }
    }
}

fun <T> PanelScope.ListBox(data: ObservableItems<T>): RList<T> =
    RList<T>().also { it.bindItems(data); panel.add(it) }

fun <T> PanelScope.ListBox(computed: Derived<List<T>>): RList<T> =
    RList<T>().also { it.bindItems(computed); panel.add(it) }

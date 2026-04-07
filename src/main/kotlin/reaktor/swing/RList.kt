package reaktor.swing

import reaktor.core.Derived
import reaktor.core.ListChange
import reaktor.core.ObservableList
import reaktor.layout.PanelScope
import javax.swing.AbstractListModel
import javax.swing.JList
import javax.swing.JScrollPane

/**
 * A reactive list view backed by an [ObservableList]. Uses fine-grained
 * change events for efficient model updates instead of rebuilding everything.
 */
class RList<T>(private val data: ObservableList<T>) : JScrollPane(), RComponent {

    private val listModel = ReactiveListModel(data)
    val jList = JList(listModel)

    init {
        setViewportView(jList)
    }

    /** Alternative constructor from a computed list (rebuilds fully on change). */
    constructor(computed: Derived<List<T>>) : this(ObservableList<T>()) {
        autoReaction("RList.computed") {
            data.setAll(computed.value)
        }
    }

    override fun removeNotify() {
        super<JScrollPane>.removeNotify()
        listModel.dispose()
        super<RComponent>.removeNotify()
    }

    private class ReactiveListModel<T>(
        private val data: ObservableList<T>
    ) : AbstractListModel<T>() {

        private var snapshot = data.items.toList()
        private val reaction: reaktor.core.Reaction

        init {
            // Use fine-grained change listener for efficient updates
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

            // Also react to version changes for full re-sync as fallback
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

        fun dispose() {
            reaction.dispose()
        }
    }
}

fun <T> PanelScope.ListBox(data: ObservableList<T>): RList<T> =
    RList(data).also { panel.add(it) }

fun <T> PanelScope.ListBox(computed: Derived<List<T>>): RList<T> =
    RList(computed).also { panel.add(it) }

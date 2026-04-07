package flowik.core

/**
 * A reactive list that emits fine-grained change events and integrates
 * with the auto-tracking system. Any reaction that reads [items], [size],
 * or iterates the list will re-run when the list is mutated.
 */
open class ObservableItems<T>(initial: List<T> = emptyList()) : Iterable<T> {

    /** Internal version counter — observing this is how reactions track "the list changed". */
    private val version = ObservableValue(0L, name = "list-version")

    private val backing = mutableListOf<T>().apply { addAll(initial) }

    /** Listeners for fine-grained change events (useful for table/list models). */
    private val changeListeners = mutableListOf<(ListChange<T>) -> Unit>()

    /** Snapshot of current items. Reading this auto-tracks. */
    val items: List<T>
        get() {
            version.value // touch the version to register dependency
            return backing.toList()
        }

    val size: Int
        get() {
            version.value
            return backing.size
        }

    fun isEmpty(): Boolean {
        version.value
        return backing.isEmpty()
    }

    operator fun get(index: Int): T {
        version.value
        return backing[index]
    }

    override fun iterator(): Iterator<T> {
        version.value
        return backing.toList().iterator()
    }

    fun add(item: T) {
        val index = backing.size
        backing.add(item)
        fireChange(ListChange.Insert(index, item))
        bumpVersion()
    }

    fun add(index: Int, item: T) {
        backing.add(index, item)
        fireChange(ListChange.Insert(index, item))
        bumpVersion()
    }

    fun removeAt(index: Int): T {
        val removed = backing.removeAt(index)
        fireChange(ListChange.Remove(index, removed))
        bumpVersion()
        return removed
    }

    fun remove(item: T): Boolean {
        val index = backing.indexOf(item)
        if (index == -1) return false
        removeAt(index)
        return true
    }

    operator fun set(index: Int, item: T): T {
        val old = backing.set(index, item)
        fireChange(ListChange.Update(index, old, item))
        bumpVersion()
        return old
    }

    fun clear() {
        val old = backing.toList()
        backing.clear()
        fireChange(ListChange.Clear(old))
        bumpVersion()
    }

    fun setAll(items: List<T>) {
        // Short-circuit if the new items are identical to current contents
        if (backing == items) return
        action {
            clear()
            items.forEach { add(it) }
        }
    }

    fun onChange(listener: (ListChange<T>) -> Unit) {
        changeListeners.add(listener)
    }

    private fun fireChange(change: ListChange<T>) {
        changeListeners.forEach { it(change) }
    }

    private fun bumpVersion() {
        version.value = version.untrackedValue + 1
    }
}

/** Fine-grained change events for [ObservableItems]. */
sealed class ListChange<T> {
    data class Insert<T>(val index: Int, val item: T) : ListChange<T>()
    data class Remove<T>(val index: Int, val item: T) : ListChange<T>()
    data class Update<T>(val index: Int, val old: T, val new: T) : ListChange<T>()
    data class Clear<T>(val old: List<T>) : ListChange<T>()
}

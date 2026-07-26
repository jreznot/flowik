package flowik.core

import kotlin.reflect.KProperty

/**
 * A reactive list that emits fine-grained change events and integrates
 * with the auto-tracking system. Any reaction that reads [items], [size],
 * or iterates the list will re-run when the list is mutated.
 */
open class ObservableList<T>(initial: List<T> = emptyList()) : Iterable<T>, MutableObservable<MutableList<T>> {

    /** Internal version counter — observing this is how reactions track "the list changed". */
    private val version = ObservableValue(0L, name = "list-version")

    private val backing = mutableListOf<T>().apply { addAll(initial) }

    /** Listeners for fine-grained change events (useful for table/list models). */
    private val changeListeners = mutableListOf<(ListChange<T>) -> Unit>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

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

    fun onChange(listener: (ListChange<T>) -> Unit): Disposable {
        changeListeners.add(listener)

        return object : Disposable {
            override fun dispose() {
                changeListeners.remove(listener)
            }
        }
    }

    private fun fireChange(change: ListChange<T>) {
        changeListeners.forEach { it(change) }
    }

    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    private fun bumpVersion() {
        version.value = version.untrackedValue + 1
        notifySubscribers()
    }

    /**
     * Notifies [subscribe] listeners *without* bumping the version, i.e. without
     * invalidating reactions that read the list contents.
     *
     * For changes that happen inside an element rather than to the list itself —
     * what [ObservableEntityList] forwards from its element wrappers.
     */
    protected fun notifySubscribers() {
        subscribers.toList().forEach { it.onChange() }
    }

    override fun get(): MutableList<T> {
        version.value
        return delegate
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): MutableList<T> {
        version.value
        return delegate
    }

    override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: MutableList<T>
    ) {
        setAll(value.toList())
    }

    override var value: MutableList<T>
        get() {
            version.value
            return delegate
        }
        set(value) {
            setAll(value.toList())
        }

    /**
     * A [MutableList] view that delegates mutating operations back to this
     * [ObservableEntityList] instance, keeping reactive tracking intact.
     */
    private val delegate: MutableList<T> by lazy { DelegateList() }

    /**
     * Mutable list implementation that delegates add/remove/clear/size and
     * other read operations to the owning [ObservableEntityList] instance.
     */
    private inner class DelegateList : AbstractMutableList<T>() {
        override val size: Int
            get() = this@ObservableList.size

        override fun get(index: Int): T = this@ObservableList[index]

        override fun add(index: Int, element: T) {
            this@ObservableList.add(index, element)
        }

        override fun removeAt(index: Int): T {
            val removed = this@ObservableList.removeAt(index)
            return removed
        }

        override fun set(index: Int, element: T): T {
            val old = this@ObservableList[index]
            this@ObservableList[index] = element
            return old
        }

        override fun clear() {
            this@ObservableList.clear()
        }
    }
}

/** Fine-grained change events for [ObservableList]. */
sealed class ListChange<T> {
    data class Insert<T>(val index: Int, val item: T) : ListChange<T>()
    data class Remove<T>(val index: Int, val item: T) : ListChange<T>()
    data class Update<T>(val index: Int, val old: T, val new: T) : ListChange<T>()
    data class Clear<T>(val old: List<T>) : ListChange<T>()
}

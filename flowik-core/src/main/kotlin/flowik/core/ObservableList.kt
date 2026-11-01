package flowik.core

import java.util.Objects
import kotlin.reflect.KProperty

/**
 * A reactive list that emits fine-grained change events and integrates
 * with the auto-tracking system. Any reaction that reads [items], [size],
 * or iterates the list will re-run when the list is mutated.
 */
open class ObservableList<T>(initial: List<T> = emptyList()) :
    Iterable<T>, MutableObservable<MutableList<T>>, ObservationHooks {

    /** Internal version counter — observing this is how reactions track "the list changed". */
    private val version = ObservableValue(0L, name = "list-version")

    // Readers observe the version counter, so it is the one to ask.
    override fun onBecomeObserved(listener: () -> Unit): Disposable = version.onBecomeObserved(listener)

    override fun onBecomeUnobserved(listener: () -> Unit): Disposable = version.onBecomeUnobserved(listener)

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

    /**
     * Appends [items] in one batch, each with a [ListChange.Insert] of its own.
     * Returns whether anything was added.
     */
    fun addAll(items: Collection<T>): Boolean = addAll(backing.size, items)

    /**
     * Inserts [items] at [index], in order, in one batch, each with a
     * [ListChange.Insert] of its own. Returns whether anything was added.
     */
    fun addAll(index: Int, items: Collection<T>): Boolean {
        if (index !in 0..backing.size) throw IndexOutOfBoundsException("Index: $index, Size: ${backing.size}")
        if (items.isEmpty()) return false
        // A copy, in case items is a view of this very list.
        val added = items.toList()
        action {
            added.forEachIndexed { offset, item -> add(index + offset, item) }
        }
        return true
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

    /**
     * Removes every item that matches [predicate], in one batch, and returns
     * whether anything was removed.
     *
     * Each item leaves with a [ListChange.Remove] of its own, so a container
     * bound with `items(list)` drops just those rows instead of rebuilding them
     * all — which a `setAll(items.filter { … })` would do — and reactions run
     * once, after the last removal.
     */
    fun removeAll(predicate: (T) -> Boolean): Boolean {
        val doomed = backing.indices.filter { predicate(backing[it]) }
        if (doomed.isEmpty()) return false
        action {
            doomed.asReversed().forEach { removeAt(it) }
        }
        return true
    }

    operator fun set(index: Int, item: T): T {
        val old = backing.set(index, item)
        fireChange(ListChange.Update(index, old, item))
        bumpVersion()
        return old
    }

    /**
     * Moves the item at [from] so that it ends up at [to], shifting the items in
     * between by one — MobX's `move`. Emits a single [ListChange.Move], so a
     * container bound with `items(list)` moves the row it already has instead of
     * rebuilding it.
     */
    fun move(from: Int, to: Int) {
        Objects.checkIndex(from, backing.size)
        Objects.checkIndex(to, backing.size)
        if (from == to) return
        val item = backing.removeAt(from)
        backing.add(to, item)
        fireChange(ListChange.Move(from, to, item))
        bumpVersion()
    }

    /**
     * Sorts the list in place by [comparator] — stably, so equal items keep their
     * order — in one batch.
     *
     * Items get where they belong through [ListChange.Move]s, so rows bound to
     * the list are reordered, not rebuilt; an item already in place does not
     * move.
     */
    fun sortWith(comparator: Comparator<in T>) {
        val sorted = backing.sortedWith(comparator)
        action {
            for (index in sorted.indices) {
                if (backing[index] === sorted[index]) continue
                // Found by identity: sorted holds the very instances of backing.
                var from = index + 1
                while (backing[from] !== sorted[index]) from++
                move(from, index)
            }
        }
    }

    /** Sorts the list in place by the natural order of [selector] — see [sortWith]. */
    fun <R : Comparable<R>> sortBy(selector: (T) -> R?) = sortWith(compareBy(selector))

    /** Sorts the list in place by the reverse natural order of [selector] — see [sortWith]. */
    fun <R : Comparable<R>> sortByDescending(selector: (T) -> R?) = sortWith(compareByDescending(selector))

    fun clear() {
        val old = backing.toList()
        backing.clear()
        fireChange(ListChange.Clear(old))
        bumpVersion()
    }

    /**
     * Replaces the contents with [items] — MobX's `replace` — through the fewest
     * changes it can find, in one batch.
     *
     * The lists are diffed: items equal to one already there stay where they are,
     * and only the rest are inserted, removed or — where a removal and an insertion
     * meet at the same spot — updated in place. A container bound with
     * `items(list)` therefore keeps the rows of the items that stayed, and a
     * `JList` keeps its selection. When the contents already match, nobody is
     * notified at all.
     *
     * An item that stays keeps its *instance*: equality is what decides that it
     * stayed, as with every write to an observable.
     */
    fun setAll(items: List<T>) {
        if (backing == items) return
        val old = backing.toList()
        // A copy, in case items is a view of this very list.
        val target = items.toList()
        val kept = matchedPairs(old, target) + intArrayOf(old.size, target.size)

        action {
            var position = 0
            var oldCursor = 0
            var newCursor = 0
            for ((oldIndex, newIndex) in kept) {
                val removed = oldIndex - oldCursor
                val inserted = newIndex - newCursor
                val replaced = minOf(removed, inserted)
                for (offset in 0 until replaced) {
                    val item = target[newCursor + offset]
                    if (backing[position] != item) set(position, item)
                    position++
                }
                repeat(removed - replaced) { removeAt(position) }
                for (offset in replaced until inserted) {
                    add(position, target[newCursor + offset])
                    position++
                }
                position++ // the kept item
                oldCursor = oldIndex + 1
                newCursor = newIndex + 1
            }
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

        override fun addAll(elements: Collection<T>): Boolean = this@ObservableList.addAll(elements)

        override fun addAll(index: Int, elements: Collection<T>): Boolean =
            this@ObservableList.addAll(index, elements)

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

    /**
     * [item] left index [from] and now sits at index [to]; the items in between
     * shifted by one towards [from]. Equivalent to a removal at [from] followed by
     * an insertion at [to].
     */
    data class Move<T>(val from: Int, val to: Int, val item: T) : ListChange<T>()
    data class Clear<T>(val old: List<T>) : ListChange<T>()
}

package flowik.core

import java.util.function.Supplier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A reactive set — the counterpart of MobX's `observable.set`.
 *
 * Elements keep insertion order (like a JS `Set`), and every read —
 * [items], [size], [isEmpty], [contains], iteration — auto-tracks, so a
 * reaction that asks `"admin" in roles` re-runs when the answer can change.
 *
 * Example:
 * ```kotlin
 * val selection = observableSet<String>()
 *
 * autoRun { println("selected: ${selection.size}") }
 *
 * selection.add("alice")      // fires
 * selection.add("alice")      // already there — nobody is notified
 * selection.toggle("alice")   // removes it, fires
 * ```
 *
 * Also, usable as a property delegate, exposing a plain [MutableSet] view whose
 * mutations go back through this instance:
 * ```kotlin
 * class Store {
 *     var tags: MutableSet<String> by observableSet("new")
 * }
 * ```
 *
 * Change detection is `equals`/`hashCode`-based, as a hash set requires. Only
 * mutations that actually change the contents notify anybody, which — unlike
 * [ObservableList] — makes the notification granularity as coarse as the set
 * semantics: adding a duplicate is not a change.
 *
 * Elements are held as plain values, so this is a *shallow* container: there is
 * no [ObservableEntity]-wrapping variant (the counterpart of [observables] for
 * lists). Wrapping would key the set on each element's initial snapshot —
 * [ObservableEntity.hashCode] is that of the wrapped value at construction — so two
 * wrappers whose properties have since diverged would still collapse into one
 * element. Model a keyed reactive collection as an [ObservableEntityList] instead.
 *
 * Implementation note: reads register a dependency on a private version atom
 * rather than on the set itself, because [Tracker.addDependency] is typed to
 * [ObservableValue] — the same delegation [ObservableList] uses. Writes
 * therefore respect [action] batching.
 */
class ObservableSet<T>(initial: Collection<T> = emptySet()) :
    Iterable<T>, MutableObservable<MutableSet<T>>, ReadWriteProperty<Any?, MutableSet<T>> {

    /** Internal version counter — observing this is how reactions track "the set changed". */
    private val version = ObservableValue(0L, name = "set-version")

    private val backing = LinkedHashSet<T>().apply { addAll(initial) }

    /** Listeners for fine-grained change events. */
    private val changeListeners = mutableListOf<(SetChange<T>) -> Unit>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    /** Snapshot of the current elements, in insertion order. Reading this auto-tracks. */
    val items: Set<T>
        get() {
            version.value // touch the version to register dependency
            return LinkedHashSet(backing)
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

    operator fun contains(item: T): Boolean {
        version.value
        return item in backing
    }

    fun containsAll(items: Collection<T>): Boolean {
        version.value
        return backing.containsAll(items)
    }

    override fun iterator(): Iterator<T> {
        version.value
        return LinkedHashSet(backing).iterator()
    }

    /** Adds [item]. Returns `true` if it was not already present. */
    fun add(item: T): Boolean {
        if (!backing.add(item)) return false
        fireChange(SetChange.Add(item))
        bumpVersion()
        return true
    }

    /** Adds every element of [items]. Returns `true` if at least one was new. */
    fun addAll(items: Collection<T>): Boolean = action {
        var changed = false
        items.forEach { if (add(it)) changed = true }
        changed
    }

    /** Removes [item]. Returns `true` if it was present. */
    fun remove(item: T): Boolean {
        if (!backing.remove(item)) return false
        fireChange(SetChange.Remove(item))
        bumpVersion()
        return true
    }

    /** Removes every element of [items]. Returns `true` if at least one was present. */
    fun removeAll(items: Collection<T>): Boolean = action {
        var changed = false
        items.forEach { if (remove(it)) changed = true }
        changed
    }

    /**
     * Removes [item] if present, adds it otherwise — the natural operation for
     * a selection set. Returns `true` if the item is in the set afterward.
     */
    fun toggle(item: T): Boolean = if (!remove(item)) {
        add(item)
        true
    } else {
        false
    }

    /** Removes all elements. Does nothing — and notifies nobody — when already empty. */
    fun clear() {
        if (backing.isEmpty()) return
        val old = LinkedHashSet(backing)
        backing.clear()
        fireChange(SetChange.Clear(old))
        bumpVersion()
    }

    /**
     * Replaces the contents with [items] — MobX's `replace`. Emits the removals
     * and additions needed to get there, so listeners see nothing at all when
     * the contents already match.
     */
    fun setAll(items: Collection<T>) {
        val target = LinkedHashSet(items)
        if (backing == target) return
        action {
            removeAll(backing - target)
            addAll(target)
        }
    }

    fun onChange(listener: (SetChange<T>) -> Unit): Disposable {
        changeListeners.add(listener)

        return object : Disposable {
            override fun dispose() {
                changeListeners.remove(listener)
            }
        }
    }

    private fun fireChange(change: SetChange<T>) {
        changeListeners.toList().forEach { it(change) }
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
        subscribers.toList().forEach { it.onChange() }
    }

    /** Tracked, so one-way bindings that read this [Supplier] inside a reaction re-run. */
    override fun get(): MutableSet<T> {
        version.value
        return delegate
    }

    /**
     * A [MutableSet] view that delegates mutating operations back to this
     * [ObservableSet] instance, keeping reactive tracking intact.
     */
    private val delegate: MutableSet<T> by lazy { DelegateSet() }

    override fun toString(): String = "ObservableSet($backing)"

    override fun getValue(thisRef: Any?, property: KProperty<*>): MutableSet<T> {
        version.value
        return delegate
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: MutableSet<T>) = setAll(value)

    override var value: MutableSet<T>
        get() {
            version.value
            return delegate
        }
        set(value) {
            setAll(value.toList())
        }

    /**
     * Mutable set implementation that delegates reads and mutations to the
     * owning [ObservableSet] instance.
     */
    private inner class DelegateSet : AbstractMutableSet<T>() {
        override val size: Int
            get() = this@ObservableSet.size

        override fun contains(element: T): Boolean = element in this@ObservableSet

        override fun add(element: T): Boolean = this@ObservableSet.add(element)

        override fun remove(element: T): Boolean = this@ObservableSet.remove(element)

        override fun clear() = this@ObservableSet.clear()

        /** Iterates a snapshot; [MutableIterator.remove] routes through the owner. */
        override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
            private val snapshot = this@ObservableSet.items.iterator()
            private var last: T? = null
            private var removable = false

            override fun hasNext(): Boolean = snapshot.hasNext()

            override fun next(): T = snapshot.next().also {
                last = it
                removable = true
            }

            @Suppress("UNCHECKED_CAST")
            override fun remove() {
                check(removable) { "next() has not been called, or remove() was already called for this element" }
                removable = false
                this@ObservableSet.remove(last as T)
            }
        }
    }
}

/** Fine-grained change events for [ObservableSet]. */
sealed class SetChange<T> {
    data class Add<T>(val item: T) : SetChange<T>()
    data class Remove<T>(val item: T) : SetChange<T>()
    data class Clear<T>(val old: Set<T>) : SetChange<T>()
}

package flowik.core

import java.util.AbstractMap.SimpleEntry
import kotlin.reflect.KProperty

/**
 * A reactive map with dynamic keys — the counterpart of MobX's `observable.map`.
 *
 * Where an [ObservableEntity] exposes the fixed properties of one object, this
 * holds any number of entries added and removed at run time: a lookup table by
 * id, a cache of per-key state, a selection keyed by row.
 *
 * ```kotlin
 * val drafts = observableMap<Long, String>()
 *
 * autoRun { println("draft of 42: ${drafts[42L]}") }   // re-runs for key 42 only
 * autoRun { println("${drafts.size} draft(s)") }       // re-runs on any change
 *
 * drafts[42L] = "Hello"      // both run
 * drafts[7L] = "Other"       // only the second one runs
 * drafts[42L] = "Hello"      // equal value — nobody is notified
 * ```
 *
 * ### Tracking granularity
 *
 * As in MobX, a lookup tracks *its key*: [get] and [containsKey] re-run when
 * that key is added, removed or given a different value, and not when another
 * key changes. Everything that looks at the whole map — [items], [size],
 * [keys], [values], iteration — re-runs on any change.
 *
 * Writes compare values with `equals`, so storing an equal value is not a
 * change. Entries keep insertion order.
 *
 * Also usable as a property delegate, exposing a plain [MutableMap] view whose
 * reads track like the methods above and whose mutations go back through this
 * instance:
 * ```kotlin
 * class Store {
 *     var drafts: MutableMap<Long, String> by observableMap()
 * }
 * ```
 */
class ObservableMap<K, V>(initial: Map<K, V> = emptyMap()) :
    Iterable<Map.Entry<K, V>>, MutableObservable<MutableMap<K, V>>, ObservationHooks {

    /** Internal version counter — observing this is how reactions track "the map changed". */
    private val version = ObservableValue(0L, name = "map-version")

    // Whole-map readers observe the version counter, so it is the one to ask.
    override fun onBecomeObserved(listener: () -> Unit): Disposable = version.onBecomeObserved(listener)

    override fun onBecomeUnobserved(listener: () -> Unit): Disposable = version.onBecomeUnobserved(listener)

    private val backing = LinkedHashMap<K, V>(initial)

    /**
     * One atom per key that some reaction looked up, bumped when that key
     * changes. Created on a tracked lookup and dropped once nothing observes
     * it, so looking up many keys over time does not accumulate atoms.
     */
    private val keyAtoms = HashMap<K, ObservableValue<Long>>()

    /** Listeners for fine-grained change events. */
    private val changeListeners = mutableListOf<(MapChange<K, V>) -> Unit>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    /** Snapshot of the current entries, in insertion order. Reading this auto-tracks. */
    val items: Map<K, V>
        get() {
            version.value
            return LinkedHashMap(backing)
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

    /** Snapshot of the keys, in insertion order. Auto-tracks the whole map. */
    val keys: Set<K>
        get() {
            version.value
            return LinkedHashSet(backing.keys)
        }

    /** Snapshot of the values, in insertion order. Auto-tracks the whole map. */
    val values: List<V>
        get() {
            version.value
            return backing.values.toList()
        }

    /** The value for [key], or `null`. Auto-tracks [key] only. */
    operator fun get(key: K): V? {
        trackKey(key)
        return backing[key]
    }

    /** Whether [key] is present. Auto-tracks [key] only. */
    fun containsKey(key: K): Boolean {
        trackKey(key)
        return backing.containsKey(key)
    }

    /** Snapshot iteration over the entries. Auto-tracks the whole map. */
    override fun iterator(): Iterator<Map.Entry<K, V>> {
        version.value
        return LinkedHashMap(backing).entries.iterator()
    }

    operator fun set(key: K, value: V) {
        put(key, value)
    }

    /**
     * Associates [value] with [key] and returns the previous value. Notifies
     * nobody when [key] already held an equal value.
     */
    fun put(key: K, value: V): V? {
        val present = backing.containsKey(key)
        val old = backing[key]
        if (present && old == value) return old
        backing[key] = value
        @Suppress("UNCHECKED_CAST")
        changed(key, if (present) MapChange.Update(key, old as V, value) else MapChange.Add(key, value))
        return old
    }

    /** Puts every entry of [from] in one batch. */
    fun putAll(from: Map<out K, V>) = action {
        from.forEach { (key, value) -> put(key, value) }
    }

    /** Removes [key] and returns its value, or `null` — notifying nobody — when it was absent. */
    fun remove(key: K): V? {
        if (!backing.containsKey(key)) return null
        @Suppress("UNCHECKED_CAST")
        val old = backing.remove(key) as V
        changed(key, MapChange.Remove(key, old))
        return old
    }

    /** Removes all entries. Does nothing — and notifies nobody — when already empty. */
    fun clear() {
        if (backing.isEmpty()) return
        val old = LinkedHashMap(backing)
        backing.clear()
        action {
            fireChange(MapChange.Clear(old))
            old.keys.forEach { bumpKey(it) }
            bumpVersion()
        }
    }

    /**
     * Replaces the contents with [entries] — MobX's `replace`. Removes the keys
     * [entries] lacks and puts the rest, so only the keys that really change
     * notify, and nobody at all when the contents already match.
     */
    fun setAll(entries: Map<out K, V>) {
        if (backing == entries) return
        action {
            (backing.keys - entries.keys).forEach { remove(it) }
            putAll(entries)
        }
    }

    fun onChange(listener: (MapChange<K, V>) -> Unit): Disposable {
        changeListeners.add(listener)

        return object : Disposable {
            override fun dispose() {
                changeListeners.remove(listener)
            }
        }
    }

    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    private fun trackKey(key: K) {
        // Nothing would ever release an atom created outside a tracker.
        if (Tracking.current == null) return
        val atom = keyAtoms.getOrPut(key) {
            ObservableValue(0L, name = "map-key").also { atom ->
                atom.onBecomeUnobserved { if (keyAtoms[key] === atom) keyAtoms.remove(key) }
            }
        }
        atom.value
    }

    /** One batch, so a reaction reading both the key and the whole map runs once. */
    private fun changed(key: K, change: MapChange<K, V>) = action {
        fireChange(change)
        bumpKey(key)
        bumpVersion()
    }

    private fun fireChange(change: MapChange<K, V>) {
        changeListeners.toList().forEach { it(change) }
    }

    private fun bumpKey(key: K) {
        keyAtoms[key]?.let { it.value = it.untrackedValue + 1 }
    }

    private fun bumpVersion() {
        version.value = version.untrackedValue + 1
        subscribers.toList().forEach { it.onChange() }
    }

    // The view itself is not a read: each of its methods tracks what it looks at,
    // so `drafts[42L]` through a delegated property still tracks key 42 only.

    override fun get(): MutableMap<K, V> = delegate

    override fun getValue(thisRef: Any?, property: KProperty<*>): MutableMap<K, V> = delegate

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: MutableMap<K, V>) = setAll(value)

    override var value: MutableMap<K, V>
        get() = delegate
        set(value) {
            setAll(LinkedHashMap(value))
        }

    override fun toString(): String = "ObservableMap($backing)"

    /**
     * A [MutableMap] view that delegates reads and mutations to the owning
     * [ObservableMap], keeping reactive tracking intact.
     */
    private val delegate: MutableMap<K, V> by lazy { DelegateMap() }

    private inner class DelegateMap : AbstractMutableMap<K, V>() {
        override val size: Int
            get() = this@ObservableMap.size

        override fun get(key: K): V? = this@ObservableMap[key]

        override fun containsKey(key: K): Boolean = this@ObservableMap.containsKey(key)

        override fun put(key: K, value: V): V? = this@ObservableMap.put(key, value)

        override fun putAll(from: Map<out K, V>) = this@ObservableMap.putAll(from)

        override fun remove(key: K): V? = this@ObservableMap.remove(key)

        override fun clear() = this@ObservableMap.clear()

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = EntrySet()
    }

    /** Iterates a snapshot; removals and [MutableMap.MutableEntry.setValue] route through the owner. */
    private inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        override val size: Int
            get() = this@ObservableMap.size

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            throw UnsupportedOperationException("Use put to add an entry")

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
            object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                private val snapshot = this@ObservableMap.items.entries.iterator()
                private var last: Entry? = null

                override fun hasNext(): Boolean = snapshot.hasNext()

                override fun next(): MutableMap.MutableEntry<K, V> =
                    snapshot.next().let { Entry(it.key, it.value) }.also { last = it }

                override fun remove() {
                    val entry = checkNotNull(last) {
                        "next() has not been called, or remove() was already called for this element"
                    }
                    last = null
                    this@ObservableMap.remove(entry.key)
                }
            }
    }

    private inner class Entry(key: K, value: V) : SimpleEntry<K, V>(key, value) {
        override fun setValue(value: V): V {
            this@ObservableMap.put(key, value)
            return super.setValue(value)
        }
    }
}

/** Fine-grained change events for [ObservableMap]. */
sealed class MapChange<K, V> {
    data class Add<K, V>(val key: K, val value: V) : MapChange<K, V>()
    data class Update<K, V>(val key: K, val old: V, val new: V) : MapChange<K, V>()
    data class Remove<K, V>(val key: K, val old: V) : MapChange<K, V>()
    data class Clear<K, V>(val old: Map<K, V>) : MapChange<K, V>()
}

package flowik.core

import java.util.function.Supplier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A reactive observable value — the atom of the Reaktor system.
 *
 * Reads are tracked automatically when a [Tracker] (reaction or computed) is
 * evaluating. Writes notify all tracked dependents.
 */
class ObservableValue<T>(initial: T, private val name: String? = null)
    : ReadWriteProperty<Any?, T>, Observable, Supplier<T> {

    private var _value: T = initial

    /** All reactions / derived that depend on this observable. */
    private val observers = linkedSetOf<Tracker>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    var value: T
        get() {
            // Register with the currently tracking reaction/computed
            Tracking.current?.addDependency(this)
            return _value
        }
        set(new) {
            if (_value != new) {
                _value = new
                notifyObservers()
            }
        }

    /** Read the current value without registering any tracking dependency. */
    internal val untrackedValue: T get() = _value

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun addObserver(tracker: Tracker) {
        observers.add(tracker)
    }

    fun removeObserver(tracker: Tracker) {
        observers.remove(tracker)
    }

    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    private fun notifyObservers() {
        // Snapshot to avoid ConcurrentModificationException
        for (tracker in observers.toList()) {
            when (tracker) {
                is Reaction<*> -> Tracking.schedule(tracker)
                is AutoRun -> Tracking.schedule(tracker)
                is When -> Tracking.schedule(tracker)
                is Computed<*> -> tracker.invalidate()
            }
        }
        subscribers.toList().forEach { it.onChange() }
    }

    override fun toString(): String = "ObservableValue(${name ?: "?"}=$_value)"

    override fun get(): T = value
}

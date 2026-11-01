package flowik.core

import kotlin.reflect.KProperty

/**
 * A reactive observable value — the atom of the Reaktor system.
 *
 * Reads are tracked automatically when a [Tracker] (reaction or computed) is
 * evaluating. Writes notify all tracked dependents, using `equals` to decide
 * whether anything changed. For identity or deep structural change detection
 * use [observableRef] / [observableStruct] instead.
 */
class ObservableValue<T>(initial: T, private val name: String? = null) :
    MutableObservable<T>, Dependency, ObservationHooks, PendingUnobservation {

    private var _value: T = initial

    /** All reactions / derived that depend on this observable. */
    private val observers = linkedSetOf<Tracker>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    override var value: T
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

    /** Created by the first hook listener; see [ObservationHooks]. */
    private var hooks: ObservationListeners? = null

    override fun addObserver(tracker: Tracker) {
        if (observers.add(tracker) && observers.size == 1) hooks?.becameObserved()
    }

    override fun removeObserver(tracker: Tracker) {
        if (observers.remove(tracker) && observers.isEmpty() && hooks != null) {
            Tracking.scheduleUnobservation(this)
        }
    }

    override fun processUnobservation() {
        if (observers.isEmpty()) hooks?.becameUnobserved()
    }

    override fun onBecomeObserved(listener: () -> Unit): Disposable = hooks().addObserved(listener)

    override fun onBecomeUnobserved(listener: () -> Unit): Disposable = hooks().addUnobserved(listener)

    private fun hooks(): ObservationListeners =
        hooks ?: ObservationListeners(this, initiallyObserved = observers.isNotEmpty()).also { hooks = it }

    /** How many trackers depend on this atom — for tests of the tracking lifecycle. */
    internal val observerCount: Int get() = observers.size

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

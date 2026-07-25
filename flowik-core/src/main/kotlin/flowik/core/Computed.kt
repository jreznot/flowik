package flowik.core

import kotlin.reflect.KProperty

/**
 * A derived (computed) value that auto-tracks its dependencies and caches
 * the result. Re-evaluates lazily when any upstream observable changes.
 *
 * A [Computed] is both a [Tracker] (it observes other observables)
 * and behaves like an observable (reactions can depend on it).
 *
 * Note that invalidation propagates unconditionally: dependents re-run when an
 * upstream observable changes, even if this computation yields the same result.
 * Use [computedStruct] / [computedRef] to notify only on an actual change.
 */
class Computed<T>(private val compute: () -> T) : Tracker, ReadableObservable<T> {

    private var cachedValue: T? = null
    private var isDirty = true
    private val dependencies = mutableSetOf<ObservableValue<*>>()

    private val observers = linkedSetOf<Tracker>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    override val value: T
        get() {
            Tracking.current?.let { tracker ->
                observers.add(tracker)
            }
            if (isDirty) {
                recompute()
            }
            @Suppress("UNCHECKED_CAST")
            return cachedValue as T
        }

    /** Called by upstream observables when they change. */
    fun invalidate() {
        if (!isDirty) {
            isDirty = true
            // Propagate invalidation downstream
            observers.toList().forEach { tracker ->
                when (tracker) {
                    is Reaction<*> -> Tracking.schedule(tracker)
                    is AutoRun -> Tracking.schedule(tracker)
                    is When -> Tracking.schedule(tracker)
                    is Computed<*> -> tracker.invalidate()
                }
            }
            for (it in subscribers.toList()) {
                it.onChange()
            }
        }
    }

    private fun recompute() {
        // Unsubscribe from old dependencies
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()

        // Track new dependencies
        Tracking.push(this)
        try {
            val newValue = compute()
            cachedValue = newValue
            isDirty = false
        } finally {
            Tracking.pop()
        }
    }

    override fun addDependency(observable: ObservableValue<*>) {
        dependencies.add(observable)
        observable.addObserver(this)
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

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun get(): T = value
}

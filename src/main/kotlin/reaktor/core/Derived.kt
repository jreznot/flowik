package reaktor.core

/**
 * A derived (computed) value that auto-tracks its dependencies and caches
 * the result. Re-evaluates lazily when any upstream observable changes.
 *
 * A [Derived] is both a [Tracker] (it observes other observables)
 * and behaves like an observable (reactions can depend on it).
 */
class Derived<T>(private val compute: () -> T) : Tracker {

    private var cachedValue: T? = null
    private var isDirty = true
    private val dependencies = mutableSetOf<ObservableValue<*>>()

    private val observers = linkedSetOf<Tracker>()

    val value: T
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
                    is Reaction -> Tracking.schedule(tracker)
                    is Derived<*> -> tracker.invalidate()
                }
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

    // ── Tracker interface ────────────────────────────────────────────

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
}

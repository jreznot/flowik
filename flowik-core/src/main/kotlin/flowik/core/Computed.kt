package flowik.core

import kotlin.reflect.KProperty

/**
 * A derived (computed) value that auto-tracks its dependencies and caches
 * the result. Re-evaluates lazily when any upstream observable changes.
 *
 * A [Computed] is both a [Tracker] (it observes other observables)
 * and a [Dependency] (reactions and other computeds can depend on it).
 *
 * ### Observed and suspended
 *
 * As in MobX, a computed only stays connected to its sources while something
 * observes it — a reaction or computed that read it, or a [subscribe]r:
 *
 * - while observed, it caches its value and is invalidated by its sources;
 * - when the last observer goes away, it *suspends*: it unsubscribes from its
 *   sources and drops the cached value. Inside an [action], or a flush of
 *   reactions, this waits until the end, so a component rebuilt in the same
 *   flush finds the value still cached;
 * - read while nothing observes it, it simply evaluates and caches nothing,
 *   since nothing would tell it when the value goes stale.
 *
 * Suspension is what makes a computed safe to create in an object that lives
 * shorter than the state it reads — a view over a long-lived store: once the
 * view's reactions are disposed, the store no longer references the computed,
 * nor whatever its closure captured.
 *
 * A [subscribe]r counts as an observer, but only learns of invalidations once
 * the value has been read since it subscribed — before that, the computed has
 * no sources to be invalidated by.
 *
 * Note that invalidation propagates unconditionally: dependents re-run when an
 * upstream observable changes, even if this computation yields the same result.
 * Use [computedStruct] / [computedRef] to notify only on an actual change.
 */
class Computed<T>(private val compute: () -> T) :
    Tracker, Dependency, ReadableObservable<T>, ObservationHooks, PendingUnobservation {

    private var cachedValue: T? = null
    private var isDirty = true

    /** Subscribed to the sources of the last evaluation, so invalidations reach it. */
    private var isConnected = false

    private val dependencies = DependencySet(this)

    private val observers = linkedSetOf<Tracker>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    private val isObserved: Boolean get() = observers.isNotEmpty() || subscribers.isNotEmpty()

    /** Created by the first hook listener; see [ObservationHooks]. */
    private var hooks: ObservationListeners? = null

    override val value: T
        get() {
            Tracking.current?.addDependency(this)
            // Nothing would invalidate a cached value: evaluate, keep nothing.
            if (!isObserved && !isConnected) return compute()
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
        isConnected = true
        dependencies.track {
            cachedValue = compute()
            isDirty = false
        }
    }

    /**
     * Tells the unobserved hooks, then disconnects from the sources — which may
     * leave them unobserved in turn — and drops the cached value. Nothing
     * happens if something started observing this computed again in the meantime.
     */
    override fun processUnobservation() {
        if (isObserved) return
        hooks?.becameUnobserved()
        if (isConnected) {
            isConnected = false
            isDirty = true
            cachedValue = null
            dependencies.clear()
        }
    }

    override fun addDependency(dependency: Dependency) {
        dependencies.add(dependency)
    }

    override fun addObserver(tracker: Tracker) {
        val wasObserved = isObserved
        if (observers.add(tracker) && !wasObserved) hooks?.becameObserved()
    }

    override fun removeObserver(tracker: Tracker) {
        if (observers.remove(tracker) && !isObserved) Tracking.scheduleUnobservation(this)
    }

    override fun onBecomeObserved(listener: () -> Unit): Disposable = hooks().addObserved(listener)

    override fun onBecomeUnobserved(listener: () -> Unit): Disposable = hooks().addUnobserved(listener)

    private fun hooks(): ObservationListeners =
        hooks ?: ObservationListeners(this, initiallyObserved = isObserved).also { hooks = it }

    /** How many trackers depend on this computed — for tests of the tracking lifecycle. */
    internal val observerCount: Int get() = observers.size

    override fun subscribe(observer: Observer): Disposable {
        val wasObserved = isObserved
        subscribers.add(observer)
        if (!wasObserved) hooks?.becameObserved()
        return object : Disposable {
            override fun dispose() {
                if (subscribers.remove(observer) && !isObserved) Tracking.scheduleUnobservation(this@Computed)
            }
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value
    }

    override fun get(): T = value
}

package flowik.core

import kotlin.reflect.KProperty

/**
 * A derived value that notifies its dependents only when the result *actually
 * changed*, according to a [Comparer] — the equivalent of MobX's
 * `computed.struct`.
 *
 * A plain [Computed] propagates *invalidation*, not change: every upstream
 * write re-runs every downstream reaction, even when the derived value is
 * identical. `computed { count.value > 5 }` therefore re-renders on every
 * increment. Wrapping it here filters that:
 *
 * ```kotlin
 * val isOverLimit = computedStruct { count.value > 5 }   // fires only on false <-> true
 * ```
 *
 * Deciding whether the value changed requires evaluating it, so — unlike
 * [Computed] — this wrapper is **not lazy**. It re-evaluates as soon as an
 * upstream observable changes, or, when the writes happen inside an [action],
 * once at the end of the batch. Reading [value] inside the batch refreshes
 * on demand, so a read never observes a stale result.
 *
 * Internal: reach it through [computedStruct], [computedRef],
 * all of which hand back a [DisposableObservable].
 */
internal class PolicyComputed<T>(
    compute: () -> T,
    private val comparer: Comparer<T>,
) : DisposableObservable<T> {

    private val inner = Computed(compute)

    /** The tracking atom — dependents observe this, never [inner]. */
    private val version = ObservableValue(0L, name = "policy-computed-version")

    private var revision = 0L

    /** `true` once [inner] has been invalidated and the refresh is still pending. */
    private var stale = false

    // Subscribed before the first read, so that read connects [inner] to its
    // sources — an unobserved computed would evaluate and keep nothing.
    private val subscription: Disposable = inner.subscribe {
        if (!stale) {
            stale = true
            Tracking.schedule(this)
        }
    }

    private var last: T = untracked { inner.value }

    override val value: T
        get() {
            // Refresh before registering the caller as a dependent: a read made
            // inside an action then sees the fresh value without scheduling the
            // reader against its own read.
            refresh()
            version.value // touch the version to register dependency
            return last
        }

    /**
     * Re-evaluate the wrapped computation if it was invalidated, and notify
     * dependents only when the new result differs from the previous one.
     */
    internal fun refresh() {
        if (!stale) return
        stale = false
        val fresh = untracked { inner.value }
        if (!comparer.equal(last, fresh)) {
            last = fresh
            version.value = ++revision
        }
    }

    override fun subscribe(observer: Observer): Disposable = version.subscribe(observer)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun get(): T = value

    /**
     * Stops observing the wrapped computation: dependents are no longer notified
     * and [value] stays frozen at the last observed result. The wrapped
     * computation, left without observers, releases its own sources too.
     */
    override fun dispose() {
        subscription.dispose()
    }

    override fun toString(): String = "PolicyComputed($last)"
}

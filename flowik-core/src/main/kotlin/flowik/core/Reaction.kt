package flowik.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

/**
 * A side effect with MobX-style reaction semantics.
 *
 * [supply] is evaluated with tracking — its observable reads become dependencies.
 * [effect] receives the supplied value and runs whenever that value *changes*,
 * as decided by [equals]: a dependency change that leaves the supplied value
 * equal re-evaluates [supply] but does not run [effect]. So
 * `reaction({ count.value > 5 }) { … }` runs its effect on `false <-> true`
 * only, not on every increment.
 *
 * [effect] is NOT tracked; observables read inside it do not become
 * dependencies. It does not run on creation unless [fireImmediately] is set.
 *
 * With a positive [delay], invalidations are coalesced: the first one schedules
 * a run on [mainContext] after [delay], and later ones until then are absorbed
 * by it — MobX's `delay` option. The run evaluates [supply] afresh, so the
 * effect sees the latest value.
 *
 * An exception thrown by either [supply] or [effect] is passed to [onError], or
 * reported as described in [onReactionError] when no handler was given — it is
 * never re-thrown to whoever wrote the observable. The reaction stays alive
 * either way and re-runs on the next change; if the creation-time evaluation of
 * [supply] failed, the next successful one counts as a change and does run
 * [effect].
 */
class Reaction<T>(
    private val name: String? = null,
    private val supply: () -> T,
    private val onError: ((Throwable) -> Unit)? = null,
    private val fireImmediately: Boolean = false,
    private val equals: Comparer<T> = Comparer.Default,
    private val delay: Duration = Duration.ZERO,
    private val mainContext: CoroutineContext = Dispatchers.Main,
    private val effect: (T) -> Unit
) : Tracker, Disposable {

    private val dependencies = DependencySet(this)
    private var isDisposed = false
    private var initialized = false

    /** Whether [last] holds a successfully supplied value to compare against. */
    private var hasValue = false
    private var last: T? = null

    /** Created by the first delayed run; cancelled on [dispose]. */
    private var delayScope: CoroutineScope? = null
    private var delayedRun: Job? = null

    /**
     * Evaluates [supply] now and runs [effect] if the value changed — or, on the
     * very first run, if [fireImmediately] is set.
     */
    fun run() {
        if (isDisposed) return

        val supplied = runCatching { dependencies.track(supply) }

        val firstRun = !initialized
        initialized = true

        // Errors are reported outside the tracking context, so reads inside the
        // error handler do not become dependencies of this reaction.
        val data = supplied.getOrElse { error ->
            reportUncaught(this, error, onError)
            return
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val changed = !hasValue || !equals.equal(last as T, data)
            last = data
            hasValue = true

            if (if (firstRun) fireImmediately else changed) effect(data)
        } catch (error: Throwable) {
            reportUncaught(this, error, onError)
        }
    }

    /** Called by [Tracking] when a dependency changed: runs now, or after [delay]. */
    internal fun invalidated() {
        if (isDisposed) return
        if (!delay.isPositive()) {
            run()
            return
        }
        if (delayedRun?.isActive == true) return

        val scope = delayScope ?: CoroutineScope(mainContext + SupervisorJob()).also { delayScope = it }
        delayedRun = scope.launch {
            kotlinx.coroutines.delay(delay)
            run()
        }
    }

    override fun dispose() {
        isDisposed = true
        delayScope?.cancel()
        delayScope = null
        delayedRun = null
        last = null
        dependencies.clear()
    }

    override fun addDependency(dependency: Dependency) {
        dependencies.add(dependency)
    }

    override fun toString(): String = "Reaction(${name ?: "anonymous"})"
}

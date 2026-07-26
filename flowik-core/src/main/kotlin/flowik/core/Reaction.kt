package flowik.core

/**
 * A side effect with MobX-style reaction semantics.

 * [supply] is evaluated with tracking — its observable reads become dependencies.
 * [effect] receives the current value and runs whenever tracked data changes.
 * [effect] is NOT tracked; observables read inside it do not become dependencies.
 * Does NOT fire on creation — only on later dependency changes.
 *
 * An exception thrown by either [supply] or [effect] is passed to [onError], or
 * logged when no handler was given — it is never re-thrown to whoever wrote the
 * observable. The reaction stays alive either way and re-runs on the next
 * change; if the creation-time evaluation of [supply] failed, the next
 * successful one counts as a change and does run [effect].
 */
class Reaction<T>(
    private val name: String? = null,
    private val supply: () -> T,
    private val onError: ((Throwable) -> Unit)? = null,
    private val effect: (T) -> Unit
) : Tracker, Disposable {

    private val dependencies = mutableSetOf<ObservableValue<*>>()
    private var isDisposed = false
    private var initialized = false

    fun run() {
        if (isDisposed) return

        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()

        Tracking.push(this)
        val supplied = try {
            runCatching(supply)
        } finally {
            Tracking.pop()
        }

        val firstRun = !initialized
        initialized = true

        // Errors are reported outside the tracking context, so reads inside the
        // error handler do not become dependencies of this reaction.
        val data = supplied.getOrElse { error ->
            reportUncaught(this, error, onError)
            return
        }

        if (firstRun) return

        try {
            effect(data)
        } catch (error: Throwable) {
            reportUncaught(this, error, onError)
        }
    }

    override fun dispose() {
        isDisposed = true
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()
    }

    override fun addDependency(observable: ObservableValue<*>) {
        dependencies.add(observable)
        observable.addObserver(this)
    }

    override fun toString(): String = "Reaction(${name ?: "anonymous"})"
}

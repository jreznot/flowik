package flowik.core

/**
 * A side effect that re-runs automatically whenever its observed dependencies
 * change.
 *
 * This is the flowik-core equivalent of MobX's `autorun`.
 *
 * An exception thrown by [effect] is passed to [onError], or logged when no
 * handler was given — it is never re-thrown to whoever wrote the observable.
 * The autoRun stays alive either way and re-runs on the next change.
 */
class AutoRun(
    private val name: String? = null,
    private val onError: ((Throwable) -> Unit)? = null,
    private val effect: () -> Unit
) : Tracker, Disposable {

    private val dependencies = mutableSetOf<ObservableValue<*>>()
    private var isDisposed = false

    /** Run the effect, tracking dependencies. */
    fun run() {
        if (isDisposed) return

        // Unsubscribe from old dependencies
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()

        // Execute and track
        Tracking.push(this)
        val result = try {
            runCatching { effect() }
        } finally {
            Tracking.pop()
        }

        // Reported outside the tracking context, so reads inside the error
        // handler do not become dependencies of this autoRun.
        result.exceptionOrNull()?.let { reportUncaught(this, it, onError) }
    }

    /** Stop this autoRun from ever running again. */
    override fun dispose() {
        isDisposed = true
        for (it in dependencies) {
            it.removeObserver(this)
        }
        dependencies.clear()
    }

    override fun addDependency(observable: ObservableValue<*>) {
        dependencies.add(observable)
        observable.addObserver(this)
    }

    override fun toString(): String = "AutoRun(${name ?: "anonymous"})"
}

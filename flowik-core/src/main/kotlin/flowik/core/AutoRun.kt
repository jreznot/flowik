package flowik.core

/**
 * A side effect that re-runs automatically whenever its observed dependencies
 * change. Unlike [Reaction], which dispatches re-runs onto the Swing EDT,
 * [AutoRun] executes synchronously on the thread that triggers the change
 * (or when the outermost [action] batch completes).
 *
 * This is the flowik-core equivalent of MobX's `autorun`.
 */
class AutoRun(
    private val name: String? = null,
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
        try {
            effect()
        } finally {
            Tracking.pop()
        }
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

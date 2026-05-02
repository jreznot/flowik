package flowik.core

/**
 * A side effect that re-runs automatically whenever its observed dependencies change.
 */
class Reaction(
    private val name: String? = null,
    private val effect: () -> Unit
) : Tracker, Disposable {

    private val dependencies = mutableSetOf<ObservableValue<*>>()
    private var isDisposed = false

    /** Run the reaction, tracking dependencies. */
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

    /** Stop this reaction from ever running again. */
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

    override fun toString(): String = "Reaction(${name ?: "anonymous"})"
}

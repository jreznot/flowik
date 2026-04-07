package flowik.core

import javax.swing.SwingUtilities

/**
 * A side effect that re-runs automatically whenever its observed dependencies
 * change. This is the bridge between the reactive core and Swing — every
 * re-run is dispatched onto the EDT via [SwingUtilities.invokeLater].
 */
class Reaction(
    private val name: String? = null,
    private val effect: () -> Unit
) : Tracker, Disposable {

    private val dependencies = mutableSetOf<ObservableValue<*>>()
    private val computedDeps = mutableSetOf<Computed<*>>()
    private var isDisposed = false

    /** Run the reaction, tracking dependencies. */
    fun run() {
        if (isDisposed) return

        // Always execute on the EDT
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater { run() }
            return
        }

        // Unsubscribe from old dependencies
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()
        computedDeps.forEach { it.removeObserver(this) }
        computedDeps.clear()

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
        for (it in computedDeps) {
            it.removeObserver(this)
        }
        computedDeps.clear()
    }

    override fun addDependency(observable: ObservableValue<*>) {
        dependencies.add(observable)
        observable.addObserver(this)
    }

    /** Also, track computed values so we can unsubscribe later. */
    fun addComputedDependency(computed: Computed<*>) {
        computedDeps.add(computed)
        computed.addObserver(this)
    }

    override fun toString(): String = "Reaction(${name ?: "anonymous"})"
}

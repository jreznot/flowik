package flowik.core

/**
 * A side effect with MobX-style reaction semantics.
 *
 * [dataTracker] is evaluated with tracking — its observable reads become dependencies.
 * [effect] receives the current value and runs whenever tracked data changes.
 * [effect] is NOT tracked; observables read inside it do not become dependencies.
 * Does NOT fire on creation — only on subsequent dependency changes.
 */
class Reaction<T>(
    private val name: String? = null,
    private val dataTracker: () -> T,
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
        val data: T
        try {
            data = dataTracker()
        } finally {
            Tracking.pop()
        }

        if (!initialized) {
            initialized = true
            return
        }

        effect(data)
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

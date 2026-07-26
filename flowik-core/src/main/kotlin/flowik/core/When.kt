package flowik.core

/**
 * A one-shot reactive effect — the flowik equivalent of MobX's `when`.
 *
 * [When] observes the [predicate] function reactively.  As soon as the
 * predicate returns `true`, the [effect] is executed **once** and the
 * reaction auto-disposes itself, releasing all tracked dependencies.
 *
 * If the predicate returns `true` on the very first evaluation, the effect
 * fires immediately and the [When] is already disposed when returned to the
 * caller.
 *
 * The returned instance implements [Disposable], so callers can cancel
 * the reaction before the predicate ever becomes `true`.
 *
 * ```kotlin
 * val price = observable(0)
 * val w = whenThen({ price.value > 100 }) {
 *     println("Price exceeded 100!")
 * }
 * // w.dispose()  ← cancel early if needed
 * ```
 *
 * An exception thrown by either [predicate] or [effect] is passed to [onError],
 * or logged when no handler was given — it is never re-thrown to whoever wrote
 * the observable. A failing [predicate] leaves the reaction armed, so it can
 * still fire once the predicate evaluates cleanly.
 */
class When(
    private val name: String? = null,
    private val predicate: () -> Boolean,
    private val onError: ((Throwable) -> Unit)? = null,
    private val effect: () -> Unit
) : Tracker, Disposable {

    private val dependencies = mutableSetOf<ObservableValue<*>>()
    private val computedDeps = mutableSetOf<Computed<*>>()
    private var isDisposed = false

    /**
     * Evaluate the [predicate] inside a tracking context.  If it returns
     * `true`, execute the [effect] and dispose.
     */
    fun run() {
        if (isDisposed) return

        // Unsubscribe from old dependencies
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()
        computedDeps.forEach { it.removeObserver(this) }
        computedDeps.clear()

        // Evaluate predicate inside a tracking context
        Tracking.push(this)
        val evaluated = try {
            runCatching(predicate)
        } finally {
            Tracking.pop()
        }

        // Errors are reported outside the tracking context, so reads inside the
        // error handler do not become dependencies of this reaction.
        val result = evaluated.getOrElse { error ->
            reportUncaught(this, error, onError)
            return
        }

        if (result) {
            // Predicate satisfied — fire once, then dispose
            dispose()
            try {
                effect()
            } catch (error: Throwable) {
                reportUncaught(this, error, onError)
            }
        }
    }

    override fun dispose() {
        isDisposed = true
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()
        computedDeps.forEach { it.removeObserver(this) }
        computedDeps.clear()
    }

    override fun addDependency(observable: ObservableValue<*>) {
        dependencies.add(observable)
        observable.addObserver(this)
    }

    /** Track computed values so we can unsubscribe later. */
    fun addComputedDependency(computed: Computed<*>) {
        computedDeps.add(computed)
        computed.addObserver(this)
    }

    override fun toString(): String = "When(${name ?: "anonymous"})"
}

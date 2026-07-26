package flowik.core

/**
 * A resource that can be disposed to release its subscriptions and
 * prevent further execution.
 *
 * Implemented by [AutoRun], [Reaction] and [When] so callers can treat
 * them uniformly when managing lifecycle (e.g., disposing a list of
 * effects when a component is removed).
 */
interface Disposable {
    /** Release all subscriptions and prevent future runs. */
    fun dispose()
}

class Bindings : Disposable {
    private val disposables = mutableListOf<Disposable>()

    fun register(disposable: Disposable) {
        disposables.add(disposable)
    }

    override fun dispose() {
        disposables.forEach { it.dispose() }
        disposables.clear()
    }
}
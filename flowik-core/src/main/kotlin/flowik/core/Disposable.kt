package flowik.core

/**
 * A resource that can be disposed to release its subscriptions and
 * prevent further execution.
 *
 * Implemented by [AutoRun] and [Reaction] so callers can treat them
 * uniformly when managing lifecycle (e.g. disposing a list of effects
 * when a component is removed).
 */
interface Disposable {
    /** Release all subscriptions and prevent future runs. */
    fun dispose()
}

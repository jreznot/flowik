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

/**
 * A group of reactions with one explicit lifetime: create them through the
 * group, then [dispose] it once — and only when — the thing they drive is gone.
 *
 * It is an interface rather than a class so that whatever owns the lifetime can
 * *be* the group: a UI panel, a view, a session object. That also makes the
 * group available as a context argument inside every member of that owner, so
 * bindings need no parameter threaded through them:
 *
 * ```kotlin
 * class Sidebar : JPanel(), Bindings by Bindings() { … }
 * ```
 *
 * Groups nest: a [Bindings] is itself a [Disposable], so [register]ing a child
 * group makes one `dispose()` at the top release everything below it.
 */
interface Bindings : Disposable {

    /**
     * Takes over [disposable]'s lifetime — it is disposed with this group — and
     * returns it, so a child can be registered where it is created:
     * `add(register(Sidebar(store)))`.
     */
    fun <T : Disposable> register(disposable: T): T

    fun autoRun(
        name: String? = null,
        onError: ((Throwable) -> Unit)? = null,
        effect: () -> Unit
    ) {
        register(flowik.core.autoRun(name, onError, effect))
    }

    fun whenThen(
        name: String? = null,
        check: () -> Boolean,
        onError: ((Throwable) -> Unit)? = null,
        effect: () -> Unit
    ) {
        register(flowik.core.whenThen(name, check, onError, effect))
    }

    fun <T> reaction(
        name: String? = null,
        supply: () -> T,
        onError: ((Throwable) -> Unit)? = null,
        effect: (T) -> Unit
    ) {
        register(flowik.core.reaction(name, supply, onError, effect))
    }
}

/** Creates an empty [Bindings] group — the standalone form. */
fun Bindings(): Bindings = BindingsGroup()

private class BindingsGroup : Bindings {
    private val disposables = mutableListOf<Disposable>()

    override fun <T : Disposable> register(disposable: T): T {
        disposables.add(disposable)
        return disposable
    }

    override fun dispose() {
        // Disposing a child group can, in principle, register more — iterate a
        // copy and clear first so nothing is disposed twice or missed.
        val pending = disposables.toList()
        disposables.clear()
        pending.forEach { it.dispose() }
    }

    override fun toString(): String = "Bindings(${disposables.size})"
}

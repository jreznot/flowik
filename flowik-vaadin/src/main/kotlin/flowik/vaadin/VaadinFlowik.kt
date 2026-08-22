/*
 * Every binding in `flowik.vaadin` registers with a `flowik.core.Bindings`
 * group, which it takes as a context parameter. Nothing listens for detach
 * events on your behalf: a component moved between layouts, or a dialog closed
 * and reopened, keeps its bindings, and they end where the code says so.
 *
 * A context parameter is satisfied by any receiver in scope, so the container
 * that holds the UI can *be* the group. Vaadin views are classes anyway, which
 * makes the mixin the whole mechanism — there is no framework panel to extend:
 *
 *     @Route("todo")
 *     class TodoView : VerticalLayout(), Bindings by Bindings() {
 *         init {
 *             add(Span().apply { text { store.statusText } })  // registers here
 *             disposeOnDetach()                                // …ends with the view
 *         }
 *     }
 *
 * Groups nest, so a child registered with `register(child)` is released by its
 * owner, and a container that builds children as it runs — `items { }` —
 * disposes the ones it drops.
 */

package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.shared.Registration
import flowik.core.Bindings
import flowik.core.Disposable
import flowik.core.FlowAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Disposes [bindings] the first time this component is detached, and stops
 * listening afterwards.
 *
 * This is the one lifecycle hook the module offers, and it is opt-in: wire it to
 * the component you know is discarded — a routed view, a dialog you do not reuse
 * — rather than to every bound component. Vaadin detaches a component whenever
 * it is moved between layouts too, which is exactly the kind of event that must
 * *not* silently kill a binding.
 */
fun <C : Component> C.disposeOnDetach(bindings: Bindings): C = apply {
    var registration: Registration? = null
    registration = addDetachListener {
        registration?.remove()
        bindings.dispose()
    }
}

/**
 * The same, for a component that is its own group — the usual shape of a view:
 *
 * ```kotlin
 * class TodoView : VerticalLayout(), Bindings by Bindings() {
 *     init { … ; disposeOnDetach() }
 * }
 * ```
 */
fun <C> C.disposeOnDetach(): C where C : Component, C : Bindings = disposeOnDetach(this)

/**
 * Disposes [component] if it owns bindings, i.e. if it is a [Disposable] — a
 * component that mixed in [Bindings].
 *
 * Containers that build their own children use this when they drop one: whoever
 * created a component owns it, and a dropped child that kept its reactions
 * alive would go on writing into a component nobody can see.
 */
internal fun disposeIfOwned(component: Any?) {
    (component as? Disposable)?.dispose()
}

/**
 * Converts this Vaadin UI instance into a coroutine dispatcher.
 */
fun UI.asCoroutineDispatcher(): CoroutineDispatcher =
    VaadinCoroutineDispatcher(this)

private class VaadinCoroutineDispatcher(private val ui: UI) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        ui.access {
            block.run()
        }
    }
}

fun UI.flowAction(
    block: suspend () -> Unit
): FlowAction {
    val context = this.asCoroutineDispatcher()
    return FlowAction(context, context, block)
}

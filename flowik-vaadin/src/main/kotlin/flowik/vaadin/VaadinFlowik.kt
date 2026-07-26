package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.component.UI
import com.vaadin.flow.shared.Registration
import flowik.core.Disposable
import flowik.core.FlowAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

private const val REACTIONS_KEY = "flowik.reactions"
private const val BINDABLE_KEY = "flowik.bindable"

internal fun Component.onDetached(action: () -> Unit) {
    var registration: Registration? = null
    registration = addDetachListener {
        registration?.remove()
        action()
    }
}

interface BindableComponent {
    /** Create an autoRun and register it for automatic disposal. */
    fun autoRun(name: String? = null, effect: () -> Unit): Disposable
}

fun Component.asBindableComponent(): BindableComponent = BindableComponentImpl(this)

fun Component.autoRun(name: String? = null, effect: () -> Unit): Disposable =
    asBindableComponent().autoRun(name, effect)

private class BindableComponentImpl(val component: Component) : BindableComponent {
    init {
        if (ComponentUtil.getData(component, BINDABLE_KEY) == null) {
            ComponentUtil.setData(component, BINDABLE_KEY, this)
            component.onDetached {
                removeNotify()
                ComponentUtil.setData(component, BINDABLE_KEY, null)
            }
        }
    }

    override fun autoRun(name: String?, effect: () -> Unit): Disposable {
        val r = flowik.core.autoRun(name, effect = effect)
        reactions().add(r)
        return r
    }

    @Suppress("UNCHECKED_CAST")
    private fun reactions(): MutableList<Disposable> {
        return ComponentUtil.getData(component, REACTIONS_KEY) as? MutableList<Disposable>
            ?: mutableListOf<Disposable>().also { ComponentUtil.setData(component, REACTIONS_KEY, it) }
    }

    private fun removeNotify() {
        val list = reactions()
        for (it in list) {
            it.dispose()
        }
        list.clear()
    }

    override fun toString(): String {
        return "BindableComponent($component)"
    }
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
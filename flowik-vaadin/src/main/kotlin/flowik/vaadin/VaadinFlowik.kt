package flowik.vaadin

import com.vaadin.flow.component.UI
import flowik.core.FlowAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

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
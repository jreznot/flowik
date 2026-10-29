package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import flowik.core.Bindings
import flowik.core.untracked
import java.util.function.Supplier

/**
 * Shows the component [map] makes for the current value of [observe], swapping
 * it whenever that value changes — the master/detail pane, the content under a
 * row of tabs.
 *
 * [map] runs untracked: what it reads while building belongs to the bindings of
 * the component it returns, not to the switch, so only [observe] decides when
 * the child is replaced. The replaced child is disposed if it owns bindings, and
 * so is the one still showing when [bindings] is disposed.
 */
context(bindings: Bindings)
fun <T> HasComponents.childSwitch(observe: Supplier<T>, map: (T) -> Component) {
    var lastValue: Any? = Unset
    var currentChild: Component? = null
    disposeChildrenWith(bindings) { listOfNotNull(currentChild) }

    bindings.autoRun("HasComponents.childSwitch") {
        val value = observe.get()
        if (value != lastValue) {
            lastValue = value
            untracked {
                currentChild?.let {
                    remove(it)
                    disposeIfOwned(it)
                }

                val child = map(value)
                currentChild = child
                add(child)
            }
        }
    }
}

private object Unset

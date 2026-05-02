package flowik.swing

import flowik.core.Reaction
import java.beans.PropertyChangeListener
import javax.swing.JComponent

private const val REACTIONS_KEY = "flowik.reactions"
private const val BINDABLE_KEY = "flowik.bindable"

internal fun JComponent.onDetached(action: () -> Unit) {
    var listener: PropertyChangeListener? = null
    listener = PropertyChangeListener { e ->
        if (e.propertyName == "ancestor" && e.newValue == null) {
            removePropertyChangeListener(listener)
            action()
        }
    }
    addPropertyChangeListener(listener)
}

interface FlowikBindableComponent {
    /** Create a reaction and register it for automatic disposal. */
    fun autoReaction(name: String? = null, effect: () -> Unit): Reaction
}

fun JComponent.asBindableComponent(): FlowikBindableComponent = BindableComponentWrapper(this)

fun JComponent.autoReaction(name: String? = null, effect: () -> Unit): Reaction =
    asBindableComponent().autoReaction(name, effect)

private class BindableComponentWrapper(val component: JComponent) : FlowikBindableComponent {
    init {
        if (component.getClientProperty(BINDABLE_KEY) == null) {
            component.putClientProperty(BINDABLE_KEY, this)
            component.onDetached {
                removeNotify()
            }
        }
    }

    /** Create a reaction and register it for automatic disposal. */
    override fun autoReaction(name: String?, effect: () -> Unit): Reaction {
        val r = flowik.core.reaction(name, effect)
        reactions().add(r)
        return r
    }

    @Suppress("UNCHECKED_CAST")
    private fun reactions(): MutableList<Reaction> {
        return component.getClientProperty(REACTIONS_KEY) as? MutableList<Reaction>
            ?: mutableListOf<Reaction>().also { component.putClientProperty(REACTIONS_KEY, it) }
    }

    private fun removeNotify() {
        val list = reactions()
        list.forEach { it.dispose() }
        list.clear()
    }
}

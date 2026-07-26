package flowik.swing

import flowik.core.Disposable
import org.slf4j.LoggerFactory
import java.beans.PropertyChangeListener
import javax.swing.JComponent

private const val REACTIONS_KEY = "flowik.reactions"
private const val BINDABLE_KEY = "flowik.bindable"

/**
 * Logger for the reaction machinery. Everything a reaction fails to handle
 * itself is reported under the `flowik.core` category, so an application can
 * route or silence it with one line of logging configuration.
 */
private val log = LoggerFactory.getLogger("flowik.swing")

internal fun JComponent.onDetached(action: () -> Unit) {
    var listener: PropertyChangeListener? = null
    listener = PropertyChangeListener { e ->
        if (e.propertyName == "ancestor" && e.newValue == null) {
            removePropertyChangeListener(listener)
            log.trace("Component {} onDetached", this)
            action()
        }
    }
    addPropertyChangeListener(listener)
}

interface BindableComponent {
    /** Create an autoRun and register it for automatic disposal. */
    fun autoRun(name: String? = null, effect: () -> Unit): Disposable
}

fun JComponent.asBindableComponent(): BindableComponent = BindableComponentImpl(this)

fun JComponent.autoRun(name: String? = null, effect: () -> Unit): Disposable =
    asBindableComponent().autoRun(name, effect)

private class BindableComponentImpl(val component: JComponent) : BindableComponent {
    init {
        if (component.getClientProperty(BINDABLE_KEY) == null) {
            component.putClientProperty(BINDABLE_KEY, this)
            component.onDetached {
                removeNotify()
                component.putClientProperty(BINDABLE_KEY, null)
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
        return component.getClientProperty(REACTIONS_KEY) as? MutableList<Disposable>
            ?: mutableListOf<Disposable>().also { component.putClientProperty(REACTIONS_KEY, it) }
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

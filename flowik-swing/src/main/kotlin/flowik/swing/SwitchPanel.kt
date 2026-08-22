package flowik.swing

import flowik.core.Bindings
import flowik.layout.PanelScope
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Shows the component [map] makes for the current value of [observe], swapping
 * it whenever that value changes.
 *
 * The replaced child is disposed if it owns bindings, so [map] is free to
 * return a [BindingsPanel].
 */
context(bindings: Bindings)
fun <T> JPanel.childSwitch(observe: Supplier<T>, map: (T) -> JComponent) {
    var lastValue: Any? = Unset
    var currentChild: JComponent? = null

    bindings.autoRun("JPanel.switch") {
        val value = observe.get()
        if (value != lastValue) {
            lastValue = value
            currentChild?.let {
                remove(it)
                disposeIfOwned(it)
            }

            val child = map(value)
            currentChild = child
            add(child, BorderLayout.CENTER)

            revalidate()
            repaint()
        }
    }
}

private object Unset

fun <T> PanelScope.Switch(observe: Supplier<T>, map: (T) -> JComponent): JPanel {
    return JPanel(BorderLayout()).also {
        it.childSwitch(observe, map)
        panel.add(it)
    }
}

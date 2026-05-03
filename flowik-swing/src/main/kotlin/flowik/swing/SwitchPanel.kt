package flowik.swing

import flowik.layout.PanelScope
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

fun <T> JPanel.childSwitch(observe: Supplier<T>, map: (T) -> JComponent) {
    var lastValue: Any? = Unset
    var currentChild: JComponent? = null

    autoRun("JPanel.switch") {
        val value = observe.get()
        if (value != lastValue) {
            lastValue = value
            currentChild?.let { remove(it) }

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

package flowik.swing

import flowik.core.Computed
import flowik.core.ObservableValue
import flowik.layout.PanelScope
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

fun <T> JPanel.bindSwitch(observe: () -> T, map: (T) -> JComponent) {
    var lastValue: Any? = Unset
    var currentChild: JComponent? = null

    autoReaction("JPanel.switch") {
        val value = observe()
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

fun <T> JPanel.bindSwitch(observable: ObservableValue<T>, map: (T) -> JComponent) =
    bindSwitch({ observable.value }, map)

fun <T> JPanel.bindSwitch(computed: Computed<T>, map: (T) -> JComponent) =
    bindSwitch({ computed.value }, map)

private object Unset

fun <T> PanelScope.Switch(observable: ObservableValue<T>, map: (T) -> JComponent): JPanel {
    return JPanel(BorderLayout()).also {
        it.bindSwitch(observable, map);
        panel.add(it)
    }
}

fun <T> PanelScope.Switch(computed: Computed<T>, map: (T) -> JComponent): JPanel {
    return JPanel(BorderLayout()).also { it.bindSwitch(computed, map); panel.add(it) }
}

fun <T> PanelScope.Switch(observe: () -> T, map: (T) -> JComponent): JPanel {
    return JPanel(BorderLayout()).also {
        it.bindSwitch(observe, map)
        panel.add(it)
    }
}

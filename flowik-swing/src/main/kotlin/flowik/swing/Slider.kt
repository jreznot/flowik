package flowik.swing

import flowik.core.ObservableValue
import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.JSlider

fun JSlider.bindValue(model: ObservableValue<Int>) {
    var updating = false
    autoRun("JSlider.sync") {
        val current = model.value
        if (value != current) {
            updating = true
            value = current
            updating = false
        }
    }
    addChangeListener {
        if (!updating && !valueIsAdjusting) action { model.value = value }
    }
}

fun PanelScope.Slider(model: ObservableValue<Int>, min: Int = 0, max: Int = 100): JSlider =
    JSlider(min, max).also { it.bindValue(model); panel.add(it) }

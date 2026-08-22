package flowik.swing

import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.JSlider

context(bindings: Bindings)
fun JSlider.value(model: MutableObservable<Int>) {
    var updating = false
    bindings.autoRun("JSlider.sync") {
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

fun PanelScope.Slider(model: MutableObservable<Int>, min: Int = 0, max: Int = 100): JSlider =
    JSlider(min, max).also { it.value(model); panel.add(it) }

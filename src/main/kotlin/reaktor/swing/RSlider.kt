package reaktor.swing

import reaktor.core.ObservableValue
import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.JSlider

/** A reactive slider with two-way binding to an observable Int. */
class RSlider(min: Int = 0, max: Int = 100) : JSlider(min, max), RComponent {

    private var updating = false

    fun bindValue(model: ObservableValue<Int>) {
        autoReaction("RSlider.sync") {
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

    override fun removeNotify() {
        super<JSlider>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.Slider(model: ObservableValue<Int>, min: Int = 0, max: Int = 100): RSlider =
    RSlider(min, max).also { it.bindValue(model); panel.add(it) }

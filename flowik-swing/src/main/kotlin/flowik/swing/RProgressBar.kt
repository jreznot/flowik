package flowik.swing

import flowik.core.ObservableValue
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import javax.swing.JProgressBar
import kotlin.reflect.KProperty0

/** A reactive progress bar bound to an observable Int (0-100). */
class RProgressBar(min: Int = 0, max: Int = 100) : JProgressBar(min, max), RComponent {

    init {
        isStringPainted = true
    }

    fun bindValue(model: ObservableValue<Int>) {
        autoReaction("RProgressBar.sync") { value = model.value }
    }

    override fun removeNotify() {
        super<JProgressBar>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.progressBar(model: ObservableValue<Int>, min: Int = 0, max: Int = 100): RProgressBar =
    RProgressBar(min, max).also { it.bindValue(model); panel.add(it) }

@Suppress("UNCHECKED_CAST")
fun PanelScope.progressBar(prop: KProperty0<Int>, min: Int = 0, max: Int = 100): RProgressBar {
    return progressBar(unwrapBinding(prop), min, max)
}

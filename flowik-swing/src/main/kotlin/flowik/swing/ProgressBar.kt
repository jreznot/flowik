package flowik.swing

import flowik.core.ObservableValue
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import javax.swing.JProgressBar
import kotlin.reflect.KProperty0

fun JProgressBar.bindValue(model: ObservableValue<Int>) {
    autoReaction("JProgressBar.sync") {
        value = model.value
    }
}

fun PanelScope.progressBar(model: ObservableValue<Int>, min: Int = 0, max: Int = 100): JProgressBar {
    return JProgressBar(min, max).also {
        it.isStringPainted = true
        it.bindValue(model)
        panel.add(it)
    }
}

@Suppress("UNCHECKED_CAST")
fun PanelScope.progressBar(prop: KProperty0<Int>, min: Int = 0, max: Int = 100): JProgressBar {
    return progressBar(unwrapBinding(prop), min, max)
}

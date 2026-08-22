package flowik.swing

import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import javax.swing.JProgressBar
import kotlin.reflect.KProperty0

context(bindings: Bindings)
fun JProgressBar.value(model: MutableObservable<Int>) {
    bindings.autoRun("JProgressBar.sync") {
        value = model.value
    }
}

fun PanelScope.progressBar(model: MutableObservable<Int>, min: Int = 0, max: Int = 100): JProgressBar {
    return JProgressBar(min, max).also {
        it.isStringPainted = true
        it.value(model)
        panel.add(it)
    }
}

fun PanelScope.progressBar(prop: KProperty0<Int>, min: Int = 0, max: Int = 100): JProgressBar {
    return progressBar(unwrapBinding(prop), min, max)
}

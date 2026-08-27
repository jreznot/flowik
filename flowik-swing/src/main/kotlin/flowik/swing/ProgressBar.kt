package flowik.swing

import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.layout.PanelScope
import javax.swing.JProgressBar

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

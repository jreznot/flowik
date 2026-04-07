package reaktor.swing

import reaktor.core.ObservableValue
import reaktor.layout.PanelScope
import javax.swing.JProgressBar

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

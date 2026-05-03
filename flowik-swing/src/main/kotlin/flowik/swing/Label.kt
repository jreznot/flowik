package flowik.swing

import flowik.layout.PanelScope
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JLabel

fun JLabel.text(provider: Supplier<String>) {
    autoRun("JLabel.text") {
        text = provider.get()
    }
}

fun JLabel.icon(provider: Supplier<Icon?>) {
    autoRun("JLabel.icon") {
        icon = provider.get()
    }
}

fun PanelScope.Label(text: String): JLabel {
    return JLabel(text).also { panel.add(it) }
}

fun PanelScope.Label(comp: Supplier<String>): JLabel {
    return JLabel().also {
        it.text(comp)
        panel.add(it)
    }
}
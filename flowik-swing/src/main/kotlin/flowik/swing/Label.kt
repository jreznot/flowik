package flowik.swing

import flowik.core.Computed
import flowik.core.ObservableValue
import flowik.layout.PanelScope
import javax.swing.Icon
import javax.swing.JLabel

fun JLabel.bindText(obs: ObservableValue<String>) {
    autoRun("JLabel.text") {
        text = obs.value
    }
}

fun JLabel.bindText(comp: Computed<String>) {
    autoRun("JLabel.text") {
        text = comp.value
    }
}

fun JLabel.bindText(provider: () -> String) {
    autoRun("JLabel.text") {
        text = provider()
    }
}

fun JLabel.bindIcon(obs: ObservableValue<Icon?>) {
    autoRun("JLabel.icon") {
        icon = obs.value
    }
}

fun PanelScope.Label(text: String): JLabel {
    return JLabel(text).also { panel.add(it) }
}

fun PanelScope.Label(obs: ObservableValue<String>): JLabel {
    return JLabel().also { it.bindText(obs); panel.add(it) }
}

fun PanelScope.Label(comp: Computed<String>): JLabel {
    return JLabel().also {
        it.bindText(comp)
        panel.add(it)
    }
}

fun PanelScope.Label(provider: () -> String): JLabel {
    return JLabel().also { it.bindText(provider); panel.add(it) }
}

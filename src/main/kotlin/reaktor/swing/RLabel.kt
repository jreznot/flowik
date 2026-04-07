package reaktor.swing

import reaktor.core.Derived
import reaktor.core.ObservableValue
import reaktor.layout.PanelScope
import javax.swing.JLabel

/**
 * A reactive label. Binds its text and/or icon to observable values
 * or computed derivations.
 */
class RLabel : JLabel(), RComponent {

    fun bindText(obs: ObservableValue<String>) {
        autoReaction("RLabel.text") { text = obs.value }
    }

    fun bindText(comp: Derived<String>) {
        autoReaction("RLabel.text") { text = comp.value }
    }

    fun bindText(provider: () -> String) {
        autoReaction("RLabel.text") { text = provider() }
    }

    fun bindIcon(obs: ObservableValue<javax.swing.Icon?>) {
        autoReaction("RLabel.icon") { icon = obs.value }
    }

    override fun removeNotify() {
        super<JLabel>.removeNotify()
        super<RComponent>.removeNotify()
    }

    companion object {
        /** Create a label bound to an observable string. */
        fun of(obs: ObservableValue<String>): RLabel = RLabel().also { it.bindText(obs) }

        /** Create a label bound to a computed string. */
        fun of(comp: Derived<String>): RLabel = RLabel().also { it.bindText(comp) }

        /** Create a label bound to a reactive lambda. */
        fun of(provider: () -> String): RLabel = RLabel().also { it.bindText(provider) }

        /** Create a plain static label. */
        fun of(text: String): RLabel = RLabel().also { it.text = text }
    }
}

fun PanelScope.Label(text: String): RLabel = RLabel.of(text).also { panel.add(it) }

fun PanelScope.Label(obs: ObservableValue<String>): RLabel = RLabel.of(obs).also { panel.add(it) }

fun PanelScope.Label(comp: Derived<String>): RLabel = RLabel.of(comp).also { panel.add(it) }

fun PanelScope.Label(provider: () -> String): RLabel = RLabel.of(provider).also { panel.add(it) }

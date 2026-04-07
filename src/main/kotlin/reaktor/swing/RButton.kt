package reaktor.swing

import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.JButton

/**
 * A reactive button. The click handler runs inside an [action] scope
 * so multiple observable writes are batched.
 */
class RButton(
    label: String,
    private val onClick: () -> Unit
) : JButton(label), RComponent {

    init {
        addActionListener {
            action { onClick() }
        }
    }

    fun bindEnabled(provider: () -> Boolean) {
        autoReaction("RButton.enabled") {
            isEnabled = provider()
        }
    }

    override fun removeNotify() {
        super<JButton>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.Button(label: String, onClick: () -> Unit): RButton =
    reaktor.swing.RButton(label, onClick).also { panel.add(it) }

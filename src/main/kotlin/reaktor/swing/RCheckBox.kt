package reaktor.swing

import reaktor.core.ObservableValue
import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.JCheckBox

/**
 * A reactive checkbox with two-way boolean binding.
 */
class RCheckBox(
    private val model: ObservableValue<Boolean>,
    label: String = ""
) : JCheckBox(label), RComponent {

    private var updating = false

    init {
        autoReaction("RCheckBox.sync") {
            val current = model.value
            if (isSelected != current) {
                updating = true
                isSelected = current
                updating = false
            }
        }

        addActionListener {
            if (!updating) {
                action { model.value = isSelected }
            }
        }
    }

    override fun removeNotify() {
        super<JCheckBox>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.CheckBox(model: ObservableValue<Boolean>, label: String = ""): RCheckBox =
    reaktor.swing.RCheckBox(model, label).also { panel.add(it) }

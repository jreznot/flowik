package flowik.swing

import flowik.core.ObservableValue
import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.JCheckBox

fun JCheckBox.bindChecked(model: ObservableValue<Boolean>) {
    var updating = false
    autoReaction("JCheckBox.sync") {
        val current = model.value
        if (isSelected != current) {
            updating = true
            isSelected = current
            updating = false
        }
    }
    addActionListener {
        if (!updating) action { model.value = isSelected }
    }
}

fun PanelScope.CheckBox(model: ObservableValue<Boolean>, label: String = ""): JCheckBox =
    JCheckBox(label).also { it.bindChecked(model); panel.add(it) }

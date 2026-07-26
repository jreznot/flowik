package flowik.swing

import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import javax.swing.JCheckBox
import kotlin.reflect.KProperty0

fun JCheckBox.bindChecked(model: MutableObservable<Boolean>) {
    var updating = false
    autoRun("JCheckBox.sync") {
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

fun PanelScope.CheckBox(model: MutableObservable<Boolean>, label: String = ""): JCheckBox =
    JCheckBox(label).also { it.bindChecked(model); panel.add(it) }

fun PanelScope.CheckBox(prop: KProperty0<Boolean>, label: String = ""): JCheckBox {
    return CheckBox(unwrapBinding(prop), label)
}

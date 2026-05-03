package flowik.swing

import flowik.core.ObservableValue
import flowik.core.action
import flowik.core.unwrapBinding
import flowik.layout.PanelScope
import java.awt.Dimension
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.reflect.KProperty0

class FixedColumnTextField(columns: Int = 20) : JTextField(columns) {
    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(super.getMaximumSize().width, pref.height)
    }
}

fun JTextField.value(model: ObservableValue<String>) {
    var updating = false
    autoRun("JTextField.sync") {
        val current = model.value
        if (text != current) {
            updating = true
            text = current
            updating = false
        }
    }
    document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = sync()
        override fun removeUpdate(e: DocumentEvent) = sync()
        override fun changedUpdate(e: DocumentEvent) = sync()
        private fun sync() {
            if (!updating) action { model.value = text }
        }
    })
}

fun PanelScope.TextField(model: ObservableValue<String>, columns: Int = 20): JTextField {
    return FixedColumnTextField(columns).also {
        it.value(model)
        panel.add(it)
    }
}

@Suppress("UNCHECKED_CAST")
fun PanelScope.TextField(prop: KProperty0<String>, columns: Int = 20): JTextField {
    return TextField(unwrapBinding(prop), columns)
}

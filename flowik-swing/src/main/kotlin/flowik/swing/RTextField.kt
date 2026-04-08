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

/**
 * A reactive text field with **two-way binding**.
 *
 * - When the observable changes programmatically, the text field updates.
 * - When the user types, the observable updates (inside an action).
 */
class RTextField(columns: Int = 20) : JTextField(columns), RComponent {

    override fun getMaximumSize(): Dimension {
        val pref = preferredSize
        return Dimension(super.getMaximumSize().width, pref.height)
    }

    private var updating = false

    fun bindValue(model: ObservableValue<String>) {
        autoReaction("RTextField.sync") {
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

    override fun removeNotify() {
        super<JTextField>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.TextField(model: ObservableValue<String>, columns: Int = 20): RTextField =
    RTextField(columns).also { it.bindValue(model); panel.add(it) }

@Suppress("UNCHECKED_CAST")
fun PanelScope.TextField(prop: KProperty0<String>, columns: Int = 20): RTextField {
    return TextField(unwrapBinding(prop), columns)
}

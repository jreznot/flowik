package flowik.swing

import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

context(bindings: Bindings)
fun JTextArea.value(model: MutableObservable<String>) {
    var updating = false
    bindings.autoRun("JTextArea.sync") {
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

fun PanelScope.TextArea(model: MutableObservable<String>, rows: Int = 4, cols: Int = 30): JTextArea =
    JTextArea(rows, cols).also { it.value(model); panel.add(JScrollPane(it)) }

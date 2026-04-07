package reaktor.swing

import reaktor.core.ObservableValue
import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * A reactive text area with two-way binding — same pattern as [RTextField].
 */
class RTextArea(
    private val model: ObservableValue<String>,
    rows: Int = 4,
    cols: Int = 30
) : JTextArea(rows, cols), RComponent {

    private var updating = false

    init {
        autoReaction("RTextArea.sync") {
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
                if (!updating) {
                    action { model.value = text }
                }
            }
        })
    }

    override fun removeNotify() {
        super<JTextArea>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.rtextArea(model: ObservableValue<String>, rows: Int = 4, cols: Int = 30): RTextArea =
    RTextArea(model, rows, cols).also { JScrollPane(it).also { sp -> panel.add(sp) } }

package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.swing.autoRun
import flowik.swing.value
import flowik.swing.visible
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

/**
 * A show-on-demand search field, bound two-way to [text].
 *
 * The field only *holds* the query; whether it is applied is up to whoever
 * reads [text] — the store ignores it while [fieldVisible] is `false`, which is
 * what makes hiding the field disable filtering without discarding the query.
 */
class FilterField(
    text: MutableObservable<String>,
    fieldVisible: Supplier<Boolean>,
    placeholder: String = "Filter by name…"
) : JPanel(BorderLayout()) {

    private val field = JTextField().apply {
        value(text)
        putClientProperty("JTextField.placeholderText", placeholder)
        putClientProperty("JTextField.leadingIcon", FontIcon.of(CoreUiFree.MAGNIFYING_GLASS, 13, ICON_FG))
        putClientProperty("JTextField.showClearButton", true)
    }

    init {
        isOpaque = false
        border = EmptyBorder(0, 8, 6, 8)
        add(field, BorderLayout.CENTER)

        visible(fieldVisible)

        // Revealing the field should put the caret in it right away.
        autoRun("filterField.focus") {
            if (fieldVisible.get()) SwingUtilities.invokeLater { field.requestFocusInWindow() }
        }
    }

    fun focusField() {
        field.requestFocusInWindow()
    }
}

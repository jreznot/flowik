package reaktor.swing

import reaktor.core.ObservableList
import reaktor.core.ObservableValue
import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * A reactive combo box bound to an [ObservableList] of items and an
 * [ObservableValue] for the current selection.
 */
class RComboBox<T>(
    private val items: ObservableList<T>,
    private val selection: ObservableValue<T?>
) : JComboBox<Any?>(), RComponent {

    private var updating = false

    init {
        // Sync items
        autoReaction("RComboBox.items") {
            updating = true
            val cbModel = DefaultComboBoxModel<Any?>()
            items.items.forEach { cbModel.addElement(it) }
            model = cbModel
            // Restore selection
            selectedItem = selection.value
            updating = false
        }

        // Sync selection from observable → swing
        autoReaction("RComboBox.selection") {
            val current = selection.value
            if (selectedItem != current) {
                updating = true
                selectedItem = current
                updating = false
            }
        }

        // Swing → observable
        addActionListener {
            if (!updating) {
                @Suppress("UNCHECKED_CAST")
                action { selection.value = selectedItem as? T }
            }
        }
    }

    override fun removeNotify() {
        super<JComboBox>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun <T> PanelScope.ComboBox(items: ObservableList<T>, selection: ObservableValue<T?>): RComboBox<T> =
    RComboBox(items, selection).also { panel.add(it) }

package reaktor.swing

import reaktor.core.ObservableItems
import reaktor.core.ObservableValue
import reaktor.core.action
import reaktor.layout.PanelScope
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * A reactive combo box.
 *
 * Call [bind] to wire both the item list and the selected-item observable.
 * Items and selection are bound together because the items reaction must
 * restore the selection after rebuilding the combo model.
 */
class RComboBox<T> : JComboBox<Any?>(), RComponent {

    private var updating = false

    fun bind(items: ObservableItems<T>, selection: ObservableValue<T?>) {
        autoReaction("RComboBox.items") {
            updating = true
            val cbModel = DefaultComboBoxModel<Any?>()
            items.items.forEach { cbModel.addElement(it) }
            model = cbModel
            selectedItem = selection.value
            updating = false
        }
        autoReaction("RComboBox.selection") {
            val current = selection.value
            if (selectedItem != current) {
                updating = true
                selectedItem = current
                updating = false
            }
        }
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

fun <T> PanelScope.ComboBox(items: ObservableItems<T>, selection: ObservableValue<T?>): RComboBox<T> =
    RComboBox<T>().also { it.bind(items, selection); panel.add(it) }

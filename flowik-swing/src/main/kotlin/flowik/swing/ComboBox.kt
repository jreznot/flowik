package flowik.swing

import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableList
import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

context(bindings: Bindings)
fun <T> JComboBox<Any?>.items(items: ObservableList<T>, selection: MutableObservable<T?>) {
    var updating = false

    bindings.autoRun("JComboBox.items") {
        updating = true
        val cbModel = DefaultComboBoxModel<Any?>()
        items.items.forEach { cbModel.addElement(it) }
        model = cbModel
        selectedItem = selection.value
        updating = false
    }
    bindings.autoRun("JComboBox.selection") {
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

fun <T> PanelScope.ComboBox(items: ObservableList<T>, selection: MutableObservable<T?>): JComboBox<Any?> =
    JComboBox<Any?>().also { it.items(items, selection); panel.add(it) }

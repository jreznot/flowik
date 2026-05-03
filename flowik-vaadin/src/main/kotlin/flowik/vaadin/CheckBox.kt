package flowik.vaadin

import com.vaadin.flow.component.checkbox.Checkbox
import flowik.core.ObservableValue
import flowik.core.action

fun Checkbox.checked(model: ObservableValue<Boolean>) {
    autoRun("Checkbox.value") {
        val current = model.value
        if (value != current) {
            value = current
        }
    }
    addValueChangeListener { event ->
        if (event.isFromClient) {
            action { model.value = event.value }
        }
    }
}
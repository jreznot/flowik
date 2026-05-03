package flowik.vaadin

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.ObservableValue
import flowik.core.action

fun TextField.value(model: ObservableValue<String>) {
    if (valueChangeMode == null) {
        valueChangeMode = ValueChangeMode.EAGER
    }
    autoRun("TextField.value") {
        val current = model.value
        if (value != current) {
            value = current
        }
    }
    addValueChangeListener { event ->
        if (event.isFromClient) {
            action { model.value = event.value ?: "" }
        }
    }
}

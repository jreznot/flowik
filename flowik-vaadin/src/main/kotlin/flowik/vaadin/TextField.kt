package flowik.vaadin

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.unwrapBinding
import kotlin.reflect.KProperty0

fun TextField.value(model: MutableObservable<String>) {
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

fun TextField.value(prop: KProperty0<String>) {
    value(unwrapBinding(prop))
}
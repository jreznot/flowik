package flowik.vaadin

import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.unwrapBinding
import kotlin.reflect.KProperty0

context(bindings: Bindings)
fun TextField.value(model: MutableObservable<String>) {
    if (valueChangeMode == null) {
        valueChangeMode = ValueChangeMode.EAGER
    }
    bindings.autoRun("TextField.value") {
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

context(bindings: Bindings)
fun TextField.value(prop: KProperty0<String>) {
    value(unwrapBinding(prop))
}

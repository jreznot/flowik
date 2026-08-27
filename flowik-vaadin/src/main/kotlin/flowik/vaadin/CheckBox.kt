package flowik.vaadin

import com.vaadin.flow.component.checkbox.Checkbox
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.unwrapBinding
import kotlin.reflect.KProperty0

context(bindings: Bindings)
fun Checkbox.checked(model: MutableObservable<Boolean>) {
    bindings.autoRun("Checkbox.value") {
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

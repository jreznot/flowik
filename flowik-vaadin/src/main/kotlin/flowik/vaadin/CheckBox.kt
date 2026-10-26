package flowik.vaadin

import com.vaadin.flow.component.checkbox.Checkbox
import flowik.core.Bindings
import flowik.core.MutableObservable
import java.util.function.Supplier

context(bindings: Bindings)
fun Checkbox.checked(model: MutableObservable<Boolean>) =
    checked(model) { model.value = it }

/** The controlled form — see `HasValue.value(value, onValueChange)`. */
context(bindings: Bindings)
fun Checkbox.checked(checked: Supplier<Boolean>, onCheckedChange: (Boolean) -> Unit) =
    bindValue("Checkbox.value", checked, onCheckedChange)

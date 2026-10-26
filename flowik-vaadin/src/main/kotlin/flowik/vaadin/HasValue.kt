package flowik.vaadin

import com.vaadin.flow.component.HasValue
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.untracked
import java.util.function.Supplier

/**
 * Two-way binding for any field — `TextArea`, `DatePicker`, `Select`,
 * `ComboBox`… — keeping its value and [model] in step.
 *
 * Only client-side edits are written back, so the model is not echoed into
 * itself when the binding sets the field. The field's own value-change mode is
 * left alone: a text field reports on blur or Enter unless told otherwise.
 */
context(bindings: Bindings)
fun <E : HasValue.ValueChangeEvent<V>, V> HasValue<E, V>.value(model: MutableObservable<V>) =
    bindValue("HasValue.value", model) { model.value = it }

/**
 * The controlled form, as in Jetpack Compose: the field shows [value] and hands
 * every edit to [onValueChange] — state goes down, events come up, and the
 * field keeps no value of its own.
 *
 * This is what lets a component take its state from whoever owns it instead of
 * reaching into a store: `SearchBox(query = store::query, onQueryChange =
 * store::search)`. The callback runs as an action. If it rejects or rewrites
 * the input, the field shows what [value] says afterwards, not what was typed.
 */
context(bindings: Bindings)
fun <E : HasValue.ValueChangeEvent<V>, V> HasValue<E, V>.value(value: Supplier<V>, onValueChange: (V) -> Unit) =
    bindValue("HasValue.value", value, onValueChange)

/** The one implementation behind every `value` / `checked` binding of the module. */
context(bindings: Bindings)
internal fun <E : HasValue.ValueChangeEvent<V>, V> HasValue<E, V>.bindValue(
    name: String,
    state: Supplier<V>,
    onValueChange: (V) -> Unit,
) {
    bindings.autoRun(name) {
        val current = state.get()
        if (value != current) {
            value = current
        }
    }
    addValueChangeListener { event ->
        if (event.isFromClient) {
            action { onValueChange(event.value) }
            // A controlled field never runs ahead of its state.
            val current = untracked { state.get() }
            if (value != current) {
                value = current
            }
        }
    }
}

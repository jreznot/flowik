package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.vaadin.focusWhen
import flowik.vaadin.value
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * A show-on-demand search field, bound two-way to [text].
 *
 * The field only *holds* the query; whether it is applied is up to whoever
 * reads [text] — the store ignores it while [fieldVisible] is `false`, which is
 * what makes hiding the field disable filtering without discarding the query.
 */
class FilterField(
    text: MutableObservable<String>,
    fieldVisible: Supplier<Boolean>,
    placeholder: String = "Filter by name…"
) : Div(), Bindings by Bindings() {

    private val field = TextField().apply {
        this.placeholder = placeholder
        prefixComponent = VaadinIcon.SEARCH.create()
        isClearButtonVisible = true
        valueChangeMode = ValueChangeMode.EAGER
        setWidthFull()
        value(text)
        // Revealing the field should put the caret in it right away.
        focusWhen(fieldVisible)
    }

    init {
        addClassName("obs-filter")
        add(field)

        visible(fieldVisible)
    }

    fun focusField() {
        field.focus()
    }
}

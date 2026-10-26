package demo.vaadin.employees

import com.vaadin.flow.component.avatar.Avatar
import com.vaadin.flow.component.avatar.AvatarVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.*
import java.util.function.Supplier

/** A colored circle with the first letter of the name, in place of a photo. */
context(bindings: Bindings)
fun EmployeeAvatar(employee: ObservableEntity<Employee>, size: AvatarVariant? = null): Avatar =
    Avatar().apply {
        addThemeVariants(AvatarVariant.AURA_FILLED)
        size?.let { addThemeVariants(it) }
        name { employee.fullName }
        abbreviation { employee.initial }
        colorIndex { employee.colorIndex }
    }

/** Avatar, name and title — a list row, the manager on the summary. */
class PersonCard(employee: ObservableEntity<Employee>) : Div(), Bindings by Bindings() {
    init {
        addClassName("person-card")
        add(
            EmployeeAvatar(employee),
            Div(
                Span().apply {
                    addClassName("person-name")
                    text { employee.fullName }
                },
                Span().apply {
                    addClassName("person-title")
                    text { employee[Employee::title].orDash() }
                },
            ),
        )
    }
}

/** An icon and one line of text: the entries of the summary column. */
context(bindings: Bindings)
fun IconItem(icon: VaadinIcon, value: Supplier<String>): Div = Div().apply {
    addClassName("icon-item")
    add(icon.create(), Span().apply { text { value.get().orDash() } })
}

/** A caption over a value, as the read-only detail sections lay them out. */
context(bindings: Bindings)
fun Field(caption: String, value: Supplier<String>): Div = Div().apply {
    addClassName("field")
    add(
        Span(caption).apply { addClassName("field-caption") },
        Span().apply {
            addClassName("field-value")
            text { value.get().orDash() }
        },
    )
}

/** A titled block of fields; `wide` fields span the whole row. */
fun Section(title: String, vararg fields: Div): Div = Div().apply {
    addClassName("detail-section")
    add(H3(title))
    add(Div(*fields).apply { addClassName("field-grid") })
}

fun Div.wide(): Div = apply { addClassName("wide") }

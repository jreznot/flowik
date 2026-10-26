package demo.vaadin.employees

import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.*
import java.util.function.Supplier

/**
 * The list side: title, Add New, search and the people.
 *
 * It owns no state. It shows [employees], [query] and [selected], and reports
 * what the user does through the callbacks, so the list can be driven by
 * anything: a store, a test, a view-local observable.
 */
class EmployeeMaster(
    employees: Supplier<List<ObservableEntity<Employee>>>,
    query: Supplier<String>,
    onQueryChange: (String) -> Unit,
    selected: Supplier<ObservableEntity<Employee>?>,
    onSelect: (ObservableEntity<Employee>) -> Unit,
    onAddNew: () -> Unit,
) : Div(), Bindings by Bindings() {

    init {
        addClassName("employee-master")

        val header = Div(
            H2("Employees"),
            Button("Add New", onAddNew).apply { addThemeVariants(ButtonVariant.PRIMARY) },
        ).apply { addClassName("master-header") }

        val search = TextField().apply {
            placeholder = "Search"
            prefixComponent = VaadinIcon.SEARCH.create()
            isClearButtonVisible = true
            valueChangeMode = ValueChangeMode.EAGER
            value(query, onQueryChange)
        }

        val list = Div().apply {
            addClassName("employee-list")
            items(employees) { employee ->
                EmployeeRow(
                    employee,
                    isSelected = { selected.get() === employee },
                    onClick = { onSelect(employee) },
                )
            }
        }

        val empty = Paragraph("No one matches the search.").apply {
            addClassName("list-empty")
            visible { employees.get().isEmpty() }
        }

        add(header, Div(search).apply { addClassName("master-search") }, list, empty)
    }
}

/**
 * One row of the list. It owns its bindings — `items` rebuilds the rows as the
 * search changes and disposes the ones it drops.
 */
class EmployeeRow(
    employee: ObservableEntity<Employee>,
    isSelected: Supplier<Boolean>,
    onClick: () -> Unit,
) : Div(), Bindings by Bindings() {
    init {
        addClassName("employee-row")
        add(register(PersonCard(employee)))
        className("selected", isSelected)
        attribute("aria-current") { if (isSelected.get()) "true" else null }
        this.onClick(onClick)
    }
}

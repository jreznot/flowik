package demo.vaadin.employees

import com.vaadin.flow.spring.annotation.SpringComponent
import com.vaadin.flow.spring.annotation.UIScope
import flowik.core.*

/** A named slice of the staff — a team, a location. */
data class Group(val name: String, val members: List<ObservableEntity<Employee>>)

/**
 * The state behind the Employees, Teams and Locations pages, and the only
 * place it changes.
 *
 * Only the route views see the store. They hand their children slices of it
 * (`store::visibleEmployees`, `store::selected`) and its methods as callbacks
 * (`store::select`) — state goes down, events come up, as with state hoisting
 * in Jetpack Compose. The setters are private, so a child can't write state
 * past the store.
 *
 * One per browser tab ([UIScope]): moving between the pages builds new views,
 * and they all read from the same store, so an edit made on one page is there
 * on the others and the selection survives a round trip.
 */
@SpringComponent
@UIScope
class EmployeeStore : Store {
    val employees = observables(*sampleEmployees().toTypedArray())

    var search by observable("", name = "search")
    var selected by observableRef<ObservableEntity<Employee>?>(null, name = "selected")

    val visibleEmployees by employees.filter { employee ->
        val query = search.trim().lowercase()
        query.isEmpty()
                || employee.fullName.lowercase().contains(query)
                || employee[Employee::title].lowercase().contains(query)
    }

    init {
        selected = employees.firstOrNull { it.fullName == "Cody Fisher" }
    }

    fun managerOf(employee: ObservableEntity<Employee>): ObservableEntity<Employee>? =
        employee[Employee::managerId]?.let { id -> employees.firstOrNull { it[Employee::id] == id } }

    /** The staff grouped by [key], groups and members in a stable order. */
    fun groupedBy(key: (ObservableEntity<Employee>) -> String): Computed<List<Group>> = computed {
        employees.groupBy { key(it).ifBlank { "Unassigned" } }
            .toSortedMap()
            .map { (name, members) -> Group(name, members) }
    }

    fun nextId(): Int = (employees.maxOfOrNull { it[Employee::id] } ?: 0) + 1

    fun updateSearch(query: String) = action { search = query }

    fun select(employee: ObservableEntity<Employee>?) = action { selected = employee }

    /** Adds [employee], clears the search so it shows, and selects it. */
    fun add(employee: ObservableEntity<Employee>) = action {
        employees.add(employee)
        search = ""
        selected = employee
    }

    /** Removes [employee]; the selection moves to the next one in the list as it is shown. */
    fun delete(employee: ObservableEntity<Employee>) = action {
        val visible = visibleEmployees
        val index = visible.indexOf(employee)
        val next = visible.getOrNull(index + 1) ?: visible.getOrNull(index - 1)
        employees.remove(employee)
        if (selected === employee) {
            selected = next
        }
    }
}

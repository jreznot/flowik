package demo.vaadin.employees

import com.vaadin.flow.component.UI
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.*
import java.util.function.Supplier

/**
 * The other two pages of the bar: the same people, grouped.
 *
 * The page shows whatever [groups] holds and reports a click through [onOpen].
 * Which grouping, and what opening a person means, is the route's business —
 * see [TeamsView] and [LocationsView].
 */
open class GroupsView(
    title: String,
    groups: Supplier<List<Group>>,
    onOpen: (ObservableEntity<Employee>) -> Unit,
) : Div(), Bindings by Bindings() {

    init {
        addClassName("groups-view")
        add(H2(title))
        add(Div().apply {
            addClassName("group-grid")
            items(groups) { group -> GroupCard(group, onOpen) }
        })
        disposeOnDetach()
    }
}

class GroupCard(
    group: Group,
    onOpen: (ObservableEntity<Employee>) -> Unit,
) : Div(), Bindings by Bindings() {
    init {
        addClassName("group-card")
        val count = group.members.size
        add(
            H3(group.name),
            Span(if (count == 1) "1 person" else "$count people").apply { addClassName("muted") },
        )
        group.members.forEach { member ->
            add(register(PersonCard(member)).apply {
                addClassName("clickable")
                onClick { onOpen(member) }
            })
        }
    }
}

/** Opening someone from a group page selects them and shows them on the Employees page. */
private fun EmployeeStore.openOnEmployeesPage(): (ObservableEntity<Employee>) -> Unit = { employee ->
    select(employee)
    UI.getCurrent().navigate(EmployeesView::class.java)
}

// A title or an address edited on the Employees page regroups these pages,
// since the groupings are computed from the same store.

@Route("teams", layout = MainLayout::class)
@PageTitle("Teams")
class TeamsView(store: EmployeeStore) : GroupsView(
    title = "Teams",
    groups = store.groupedBy { it[Employee::title] },
    onOpen = store.openOnEmployeesPage(),
)

@Route("locations", layout = MainLayout::class)
@PageTitle("Locations")
class LocationsView(store: EmployeeStore) : GroupsView(
    title = "Locations",
    groups = store.groupedBy { it.nested(Employee::address)[Address::city] },
    onOpen = store.openOnEmployeesPage(),
)

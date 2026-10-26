package demo.vaadin.employees

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouteAlias
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.core.observable
import flowik.core.observableRef
import flowik.core.viewmodel.createViewModel
import flowik.vaadin.childSwitch
import flowik.vaadin.disposeOnDetach

/**
 * The master/detail page from the Vaadin docs' "Compose with Components": the
 * list on the left, whoever is selected on the right.
 *
 * This is the one stateful component of the page, the counterpart of a screen
 * composable holding its view model. It reads [store] and wires it to children
 * that know nothing about it: each gets the slices of state it shows and a
 * callback for every event it raises. [EmployeeMaster] and [EmployeeDetail]
 * never see each other either — both follow `store.selected`, so a click in
 * the list, a new employee or a deletion move the detail pane the same way.
 */
@Route("employees", layout = MainLayout::class)
@RouteAlias("", layout = MainLayout::class)
@PageTitle("Employees")
class EmployeesView(
    private val store: EmployeeStore
) : Div(), Bindings by Bindings() {

    /**
     * UI state, hoisted only as far as it has to go: the detail pane is rebuilt
     * for every person, so the tab lives here to survive a move down the list.
     * Nothing outside this page needs it, so it stays out of the store.
     */
    private val detailTab = observableRef(DetailTab.PERSONAL, name = "detailTab")

    init {
        addClassName("employees-view")
        add(
            register(
                EmployeeMaster(
                    employees = store::visibleEmployees,
                    query = store::search,
                    onQueryChange = store::updateSearch,
                    selected = store::selected,
                    onSelect = store::select,
                    onAddNew = ::addNew,
                )
            ),
            Div().apply {
                addClassName("detail-pane")
                childSwitch(store::selected) { employee ->
                    if (employee == null) NoSelection() else detail(employee)
                }
            },
        )
        disposeOnDetach()
    }

    private fun detail(employee: ObservableEntity<Employee>) = EmployeeDetail(
        employee = employee,
        manager = { store.managerOf(employee) },
        tab = detailTab,
        onTabChange = { detailTab.value = it },
        onOpenPerson = store::select,
        onEdit = { edit(employee) },
        onShare = { share(employee) },
        onDelete = { store.delete(employee) },
    )

    /** The new person exists only in the dialog until Save; Cancel leaves nothing behind. */
    private fun addNew() {
        val draft = observable(newEmployee(store.nextId()))
        val form = createViewModel(draft)
        EmployeeEditor(form, title = "New Employee") {
            form.submit()
            store.add(draft)
        }.open()
    }

    private fun edit(employee: ObservableEntity<Employee>) {
        val form = createViewModel(employee)
        EmployeeEditor(form, title = "Edit ${employee.fullName}", onSave = form::submit).open()
    }

    private fun share(employee: ObservableEntity<Employee>) {
        Notification.show(
            "A link to ${employee.fullName}'s profile was copied",
            3000,
            Notification.Position.BOTTOM_START
        )
    }
}

private fun NoSelection(): Div = Div(Paragraph("Select an employee to see their details.")).apply {
    addClassName("no-selection")
}

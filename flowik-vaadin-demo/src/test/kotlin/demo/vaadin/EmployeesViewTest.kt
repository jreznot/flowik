package demo.vaadin

import com.vaadin.flow.component.ClickEvent
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.tabs.Tabs
import demo.vaadin.employees.*
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.core.observable
import flowik.core.observableRef
import flowik.core.observables
import flowik.core.viewmodel.createViewModel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmployeesViewTest {

    private fun Component.descendants(): List<Component> =
        children.toList().flatMap { listOf(it) + it.descendants() }

    private fun Component.hasClass(name: String) = element.classList.contains(name)

    private fun Component.rows() = descendants().filter { it.hasClass("employee-row") }

    private fun Component.button(text: String) = descendants().filterIsInstance<Button>().single { it.text == text }

    private fun Component.click() = ComponentUtil.fireEvent(this, ClickEvent(this))

    private fun EmployeesView.detailName() =
        descendants().single { it.hasClass("employee-detail") }.descendants().filterIsInstance<H2>().single().text

    // The page, wired to the store

    @Test
    fun `search, selection and deletion all move the page`() {
        val store = EmployeeStore()
        val view = EmployeesView(store)

        assertEquals(15, view.rows().size)
        assertEquals("Cody Fisher", view.detailName())
        assertEquals(1, view.rows().count { it.hasClass("selected") })

        store.updateSearch("tester")
        assertEquals(3, view.rows().size)

        val first = view.rows().first()
        first.click()
        assertEquals("Eleanor Pena", view.detailName())
        assertTrue(first.hasClass("selected"))

        view.button("Delete").click()
        assertEquals(2, view.rows().size)
        assertEquals("Ralph Edwards", view.detailName())
    }

    @Test
    fun `the tab is kept while the selection moves`() {
        val store = EmployeeStore()
        val view = EmployeesView(store)

        view.descendants().filterIsInstance<Tabs>().single().selectedIndex = DetailTab.EMERGENCY.ordinal
        assertTrue(view.descendants().any { it is EmergencyTab })

        store.select(store.employees.items.first())
        assertEquals("Esther Howard", view.detailName())
        assertEquals(DetailTab.EMERGENCY.ordinal, view.descendants().filterIsInstance<Tabs>().single().selectedIndex)
        assertTrue(view.descendants().any { it is EmergencyTab })
    }

    // The parts on their own: no store, just state and callbacks

    @Test
    fun `the master shows what it is given and reports what the user does`() {
        val people = observables(*sampleEmployees().take(3).toTypedArray())
        val query = observable("")
        val selected = observableRef<ObservableEntity<Employee>?>(null)
        val events = mutableListOf<String>()

        val master = EmployeeMaster(
            employees = { people.items.filter { it.fullName.contains(query.value) } },
            query = query,
            onQueryChange = { events += "query $it" },
            selected = selected,
            onSelect = { events += "select ${it.fullName}" },
            onAddNew = { events += "add" },
        )
        assertEquals(3, master.rows().size)

        // A click is only reported: the master does not select by itself.
        master.rows()[1].click()
        assertEquals(listOf("select Jane Cooper"), events)
        assertEquals(0, master.rows().count { it.hasClass("selected") })

        // Its owner does, and the row follows.
        action { selected.value = people.items[1] }
        assertTrue(master.rows()[1].hasClass("selected"))

        action { query.value = "Kristin" }
        assertEquals(1, master.rows().size)

        master.button("Add New").click()
        assertEquals(listOf("select Jane Cooper", "add"), events)
    }

    @Test
    fun `the editor leaves saving to its caller`() {
        val employee = observable(sampleEmployees().first())
        val form = createViewModel(employee)
        var saves = 0
        val editor = EmployeeEditor(form, title = "Edit") { saves++ }

        val save = editor.footer.element.children
            .map { it.component.get() }
            .toList()
            .filterIsInstance<Button>()
            .single { it.text == "Save" }
        assertFalse(save.isEnabled)

        action { form[Employee::firstName] = "Estelle" }
        assertTrue(save.isEnabled)
        assertEquals("Esther", employee[Employee::firstName])

        save.click()
        assertEquals(1, saves)
        // The caller did not submit, so the person is untouched.
        assertEquals("Esther", employee[Employee::firstName])
    }

    @Test
    fun `the detail pane asks for the manager and reports a click on them`() {
        val people = observables(*sampleEmployees().take(4).toTypedArray())
        val cody = people.items[3]
        val opened = mutableListOf<String>()

        val detail = EmployeeDetail(
            employee = cody,
            manager = { people.items.first() },
            tab = { DetailTab.PERSONAL },
            onTabChange = {},
            onOpenPerson = { opened += it.fullName },
            onEdit = {},
            onShare = {},
            onDelete = {},
        )

        detail.descendants().single { it.hasClass("clickable") }.click()
        assertEquals(listOf("Esther Howard"), opened)
        assertNull(detail.descendants().firstOrNull { it is JobTab })
    }

    @Test
    fun `an edit saved through the page reaches the list and the detail pane`() {
        val store = EmployeeStore()
        val view = EmployeesView(store)
        val cody = store.selected!!

        val form = createViewModel(cody)
        action { form[Employee::firstName] = "Codie" }
        assertEquals("Cody Fisher", view.detailName())

        form.submit()
        assertEquals("Codie Fisher", view.detailName())
        assertTrue(view.rows().any { row -> row.descendants().any { it.element.text == "Codie Fisher" } })
    }
}

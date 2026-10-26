package demo.vaadin.employees

import com.vaadin.flow.component.avatar.AvatarVariant
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.tabs.Tabs
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.*
import java.util.function.Supplier

enum class DetailTab(val title: String) {
    PERSONAL("Personal"),
    JOB("Job"),
    EMERGENCY("Emergency"),
    DOCUMENTS("Documents"),
}

/**
 * Header, tabs, the selected tab's content and the action bar for one employee.
 *
 * Everything it shows comes in as a parameter. [employee] is a live view of one
 * person, and [manager] is looked up by whoever owns the staff list. Everything
 * the user can do goes out as a callback; the pane decides nothing on its own.
 */
class EmployeeDetail(
    private val employee: ObservableEntity<Employee>,
    private val manager: Supplier<ObservableEntity<Employee>?>,
    private val tab: Supplier<DetailTab>,
    private val onTabChange: (DetailTab) -> Unit,
    private val onOpenPerson: (ObservableEntity<Employee>) -> Unit,
    private val onEdit: () -> Unit,
    private val onShare: () -> Unit,
    private val onDelete: () -> Unit,
) : Div(), Bindings by Bindings() {

    init {
        addClassName("employee-detail")
        add(header(), content(), footer())
    }

    private fun header(): Div = Div(
        Div(
            EmployeeAvatar(employee, AvatarVariant.XLARGE),
            Div(
                H2().apply { text { employee.fullName } },
                Span().apply {
                    addClassName("detail-title")
                    text { employee[Employee::title].orDash() }
                },
            ),
        ).apply { addClassName("detail-person") },
        Tabs().apply { items(DetailTab.entries, tab, onTabChange) { it.title } },
    ).apply { addClassName("detail-header") }

    private fun content(): Div = Div().apply {
        addClassName("detail-content")
        childSwitch(tab) { selected ->
            when (selected) {
                DetailTab.PERSONAL -> PersonalTab(employee, manager, onOpenPerson)
                DetailTab.JOB -> JobTab(employee, manager)
                DetailTab.EMERGENCY -> EmergencyTab(employee)
                DetailTab.DOCUMENTS -> DocumentsTab(employee)
            }
        }
    }

    private fun footer(): Div = Div(
        Button("Edit", onEdit),
        Button("Share", onShare),
        Button("Delete", onDelete).apply { addThemeVariants(ButtonVariant.ERROR) },
    ).apply { addClassName("detail-footer") }
}

/** The tab in the screenshot: a summary column beside the basic, address and contact sections. */
class PersonalTab(
    employee: ObservableEntity<Employee>,
    manager: Supplier<ObservableEntity<Employee>?>,
    onOpenPerson: (ObservableEntity<Employee>) -> Unit,
) : Div(), Bindings by Bindings() {

    init {
        addClassName("personal-tab")
        val address = employee.nested(Employee::address)

        val summary = Div(
            H3("Summary"),
            IconItem(VaadinIcon.PHONE) { employee[Employee::workPhone] },
            IconItem(VaadinIcon.ENVELOPE_OPEN) { employee[Employee::workEmail] },
            Hr(),
            IconItem(VaadinIcon.CALENDAR) { employee[Employee::startDate].display() },
            IconItem(VaadinIcon.HOURGLASS) { tenure(employee[Employee::startDate]) },
            Hr(),
            IconItem(VaadinIcon.BRIEFCASE) { employee[Employee::employmentType].label },
            IconItem(VaadinIcon.USERS) { employee[Employee::department] },
            IconItem(VaadinIcon.CLOCK) { localTime(employee[Employee::timeZone]) },
            Hr(),
            H3("Manager"),
            Div().apply {
                childSwitch(manager) { boss ->
                    if (boss == null) {
                        Span("No manager").apply { addClassName("muted") }
                    } else {
                        PersonCard(boss).apply {
                            addClassName("clickable")
                            onClick { onOpenPerson(boss) }
                        }
                    }
                }
            },
        ).apply { addClassName("summary") }

        val sections = Div(
            Section(
                "Basic Information",
                Field("First Name") { employee[Employee::firstName] },
                Field("Middle Name") { employee[Employee::middleName] },
                Field("Last Name") { employee[Employee::lastName] },
                Field("Preferred Name") { employee[Employee::preferredName] },
                Field("Birth Date") { employee[Employee::birthDate].display() },
                Field("Gender") { employee[Employee::gender] },
                Field("Dietary Notes") { employee[Employee::dietaryNotes] }.wide(),
            ),
            Section(
                "Home Address",
                Field("Street Address") { address[Address::street] }.wide(),
                Field("City") { address[Address::city] },
                Field("Province") { address[Address::province] },
                Field("Postal Code") { address[Address::postalCode] },
                Field("Country") { address[Address::country] },
            ),
            Section(
                "Contact",
                Field("Work Phone") { employee[Employee::workPhone] },
                Field("Mobile Phone") { employee[Employee::mobilePhone] },
                Field("Home Phone") { employee[Employee::homePhone] },
                Div().apply { addClassName("field") },
                Field("Work Email") { employee[Employee::workEmail] }.wide(),
                Field("Slack") { employee[Employee::slack] }.wide(),
            ),
        ).apply { addClassName("sections") }

        add(summary, sections)
    }
}

class JobTab(
    employee: ObservableEntity<Employee>,
    manager: Supplier<ObservableEntity<Employee>?>,
) : Div(), Bindings by Bindings() {

    init {
        addClassName("single-column-tab")
        add(
            Section(
                "Position",
                Field("Title") { employee[Employee::title] },
                Field("Department") { employee[Employee::department] },
                Field("Employment Type") { employee[Employee::employmentType].label },
                Field("Manager") { manager.get()?.fullName ?: "" },
            ),
            Section(
                "Tenure",
                Field("Start Date") { employee[Employee::startDate].display() },
                Field("Time at Company") { tenure(employee[Employee::startDate]) }.wide(),
                Field("Time Zone") { employee[Employee::timeZone] },
            ),
        )
    }
}

class EmergencyTab(employee: ObservableEntity<Employee>) : Div(), Bindings by Bindings() {
    init {
        addClassName("single-column-tab")
        add(
            Section(
                "Emergency Contact",
                Field("Name") { employee[Employee::emergencyName] },
                Field("Relationship") { employee[Employee::emergencyRelation] },
                Field("Phone") { employee[Employee::emergencyPhone] },
            ),
        )
    }
}

class DocumentsTab(employee: ObservableEntity<Employee>) : Div(), Bindings by Bindings() {
    init {
        addClassName("single-column-tab")
        val documents = listOf("Employment Contract", "Non-Disclosure Agreement", "Tax Card")
        add(
            Div(
                H3("Documents"),
                *documents.map { document ->
                    IconItem(VaadinIcon.FILE_TEXT) { "$document — ${employee.fullName}.pdf" }
                }.toTypedArray(),
            ).apply { addClassName("detail-section") },
        )
    }
}

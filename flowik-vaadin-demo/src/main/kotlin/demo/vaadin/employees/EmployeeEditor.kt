package demo.vaadin.employees

import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import flowik.core.Bindings
import flowik.core.viewmodel.ViewModel
import flowik.vaadin.*
import kotlin.reflect.KProperty1

/**
 * The form for one employee, bound to an edit buffer it is given.
 *
 * The caller owns the buffer — a view model over an existing person, or over a
 * draft that does not exist yet — and decides what [onSave] means: submit it,
 * add the draft to the list, both. The dialog only edits and reports. Cancel
 * needs no callback, because nothing reaches the model until [onSave] submits it.
 */
class EmployeeEditor(
    private val form: ViewModel<Employee>,
    title: String,
    onSave: () -> Unit,
) : Dialog(), Bindings by Bindings() {

    init {
        headerTitle = title
        width = "640px"

        add(FormLayout().apply {
            setResponsiveSteps(FormLayout.ResponsiveStep("0", 1), FormLayout.ResponsiveStep("480px", 2))
            add(
                field("First Name", Employee::firstName),
                field("Last Name", Employee::lastName),
                field("Preferred Name", Employee::preferredName),
                field("Title", Employee::title),
                field("Work Phone", Employee::workPhone),
                field("Work Email", Employee::workEmail),
            )
            add(TextArea("Dietary Notes").apply { value(form.property(Employee::dietaryNotes)) }, 2)
        })

        footer.add(
            Button("Cancel") { close() },
            Button("Save") {
                onSave()
                close()
            }.apply {
                addThemeVariants(ButtonVariant.PRIMARY)
                enabled { form.isDirty }
            },
        )

        // A closed dialog is detached and never reopened: its bindings go with it.
        disposeOnDetach()
    }

    private fun field(label: String, property: KProperty1<Employee, String>): TextField =
        TextField(label).apply { value(form.property(property)) }
}

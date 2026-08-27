package demo.vaadin

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.html.Paragraph
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import flowik.core.*
import flowik.vaadin.*

data class TodoItem(val text: String, val done: Boolean = false)

/**
 * One row of the list, with a group of its own: the list rebuilds its rows
 * whenever the filter changes, and `items` disposes the rows it drops — so a row
 * that is no longer on screen stops following its item.
 */
private class TodoRow(
    item: ObservableEntity<TodoItem>,
    onRemove: () -> Unit
) : HorizontalLayout(), Bindings by Bindings() {

    init {
        setWidthFull()
        alignItems = FlexComponent.Alignment.CENTER
        isPadding = false
        isSpacing = true

        val checkbox = Checkbox().apply {
            checked(item.property(TodoItem::done))
        }

        val text = Span().apply {
            text(item.property(TodoItem::text))
            element.style.set("flex-grow", "1")

            autoRun("TodoRow.style") {
                val done = item[TodoItem::done]
                style.set("text-decoration", if (done) "line-through" else "none")
                style.set("color", if (done) "var(--lumo-secondary-text-color)" else "")
            }
        }

        val remove = Button("✕") { onRemove() }
        remove.element.setAttribute("theme", "tertiary small")

        add(checkbox, text, remove)
    }
}

/**
 * The view owns the reactions built inside it: it *is* the [Bindings] group, so
 * every binding below finds it as their context argument, and the group is
 * released when the routed view is detached — which for a route means the user
 * navigated away and this instance is gone.
 */
@Route("todo")
@PageTitle("Todo")
class TodoView : VerticalLayout(), Bindings by Bindings() {
    private val store = object : Store {
        private val todos = observables<TodoItem>()

        var filter by observable("", name = "filter")
        var showCompleted by observable(true, name = "showCompleted")
        var showFilter by observable(false, name = "showFilter")

        val visibleItems by todos.filter { item: ObservableEntity<TodoItem> ->
            val filterText = filter.lowercase()
            (showCompleted || !item[TodoItem::done])
                    && (filterText.isEmpty() || item[TodoItem::text].lowercase().contains(filterText))
        }

        private val doneTodos by todos.filter { it[TodoItem::done] }

        val totalCount by computed { todos.size }
        val doneCount by computed { doneTodos.size }
        val statusText by computed { "$doneCount / $totalCount completed" }

        fun addItem(text: String) = action {
            if (text.isNotBlank()) todos.add(TodoItem(text.trim()))
        }

        fun removeItem(item: ObservableEntity<TodoItem>) = action { todos.remove(item) }

        fun clearCompleted() = action {
            doneTodos.forEach { todos.remove(it) }
        }
    }

    init {
        store.addItem("Learn Kotlin reactive programming")
        store.addItem("Build Reaktor component library")
        store.addItem("Write unit tests")

        isPadding = false
        isSpacing = false
        setSizeFull()
        alignItems = FlexComponent.Alignment.CENTER
        style.set("padding-top", "var(--lumo-space-m)")

        add(buildMenuBar(), buildPanel())
        disposeOnDetach()
    }

    private fun buildMenuBar(): MenuBar = MenuBar().apply {
        width = "640px"
        addItem("About") { showAboutDialog() }
    }

    private fun buildPanel(): VerticalLayout = VerticalLayout().apply {
        width = "640px"
        isPadding = true
        isSpacing = true
        element.style.apply {
            set("border", "1px solid var(--lumo-contrast-30pct, #c8ccd0)")
            set("border-radius", "var(--lumo-border-radius-m, 6px)")
            set("background-color", "var(--lumo-base-color, #ffffff)")
            set("box-shadow", "0 1px 2px rgba(0, 0, 0, 0.06)")
            set("box-sizing", "border-box")
        }

        add(
            buildHeader(),
            buildFilterPanel(),
            Hr(),
            buildTodoList(),
            buildAddRow(),
            Hr(),
            buildFooter()
        )
    }

    private fun showAboutDialog() {
        val dialog = Dialog().apply {
            headerTitle = "About"
            width = "420px"
            add(
                Paragraph(
                    "This is a small Todo demo built with Vaadin Flow and Reaktor — " +
                            "a Kotlin reactive-state library inspired by MobX."
                ),
                Paragraph(
                    "State lives in plain observables; the UI subscribes via autoRun " +
                            "and rebinds itself whenever the observed values change."
                )
            )
            footer.add(Button("Close") { close() })
        }
        dialog.open()
    }

    private fun buildHeader(): HorizontalLayout = HorizontalLayout().apply {
        setWidthFull()
        alignItems = FlexComponent.Alignment.CENTER

        val title = H1("Todo").apply {
            style.set("margin", "0")
            style.set("font-size", "var(--lumo-font-size-xxl)")
        }

        val spacer = Span().apply { element.style.set("flex-grow", "1") }

        val toggle = Button("") { store.showFilter = !store.showFilter }
        toggle.text { if (store.showFilter) "Hide filter" else "Show filter" }
        toggle.icon { if (store.showFilter) VaadinIcon.CHEVRON_UP else VaadinIcon.CHEVRON_DOWN }

        add(title, spacer, toggle)
    }

    private fun buildFilterPanel(): HorizontalLayout = HorizontalLayout().apply {
        alignItems = FlexComponent.Alignment.CENTER
        isPadding = false

        val label = Span("Filter:")
        val filterField = TextField().apply { value(store::filter.asObservable()) }
        val showCompletedCb = Checkbox("Show completed").apply {
            checked(store::showCompleted.asObservable())
        }

        add(label, filterField, showCompletedCb)
        visible(store::showFilter)
    }

    private fun buildTodoList(): VerticalLayout = VerticalLayout().apply {
        isPadding = false
        isSpacing = false
        setWidthFull()

        items(store::visibleItems) { item ->
            TodoRow(item) { store.removeItem(item) }
        }
    }

    private fun buildAddRow(): HorizontalLayout = HorizontalLayout().apply {
        setWidthFull()
        alignItems = FlexComponent.Alignment.CENTER

        val input = observable("", name = "newItemInput")
        val label = Span("New todo:")
        val field = TextField().apply {
            value(input)
            element.style.set("flex-grow", "1")
        }
        val addBtn = Button("Add") {
            store.addItem(input.value)
            input.value = ""
            field.focus()
        }

        add(label, field, addBtn)
    }

    private fun buildFooter(): HorizontalLayout = HorizontalLayout().apply {
        setWidthFull()
        alignItems = FlexComponent.Alignment.CENTER

        val status = Span().apply { text(store::statusText) }
        add(status)

        val spacer = Span().apply { element.style.set("flex-grow", "1") }
        add(spacer)

        val clear = Button("Clear completed") { store.clearCompleted() }
        add(clear)
    }
}

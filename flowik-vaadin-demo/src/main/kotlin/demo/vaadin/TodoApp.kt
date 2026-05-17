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

class TodoStore {
    val todos = observables<TodoItem>()
    val filter = observable("", name = "filter")
    val showCompleted = observable(true, name = "showCompleted")
    val showFilter = observable(false, name = "showFilter")

    val visibleItems = todos.filter { item: ObservableMap<TodoItem> ->
        val filterText = filter.value.lowercase()
        (showCompleted.value || !item[TodoItem::done].value)
                && (filterText.isEmpty() || item[TodoItem::text].value.lowercase().contains(filterText))
    }

    private val doneTodos = todos.filter { it[TodoItem::done].value }

    val totalCount = computed { todos.size }
    val doneCount = computed { doneTodos.value.size }
    val statusText = computed { "${doneCount.value} / ${totalCount.value} completed" }

    fun addItem(text: String) = action {
        if (text.isNotBlank()) todos.add(TodoItem(text.trim()))
    }

    fun removeItem(item: ObservableMap<TodoItem>) = action { todos.remove(item) }

    fun toggleItem(item: ObservableMap<TodoItem>) = action {
        item[TodoItem::done].toggle()
    }

    fun clearCompleted() = action {
        doneTodos.value.forEach { todos.remove(it) }
    }
}

@Route("todo")
@PageTitle("Todo")
class TodoView : VerticalLayout() {
    private val store = TodoStore()

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

        val toggle = Button("") { store.showFilter.toggle() }
        toggle.text { if (store.showFilter.value) "Hide filter" else "Show filter" }
        toggle.icon { if (store.showFilter.value) VaadinIcon.CHEVRON_UP else VaadinIcon.CHEVRON_DOWN }

        add(title, spacer, toggle)
    }

    private fun buildFilterPanel(): HorizontalLayout = HorizontalLayout().apply {
        alignItems = FlexComponent.Alignment.CENTER
        isPadding = false

        val label = Span("Filter:")
        val filterField = TextField().apply { value(store.filter) }
        val showCompletedCb = Checkbox("Show completed").apply {
            checked(store.showCompleted)
        }

        add(label, filterField, showCompletedCb)
        visible(store.showFilter)
    }

    private fun buildTodoList(): VerticalLayout = VerticalLayout().apply {
        isPadding = false
        isSpacing = false
        setWidthFull()

        items(store.visibleItems) {
            todoRow(it)
        }
    }

    private fun todoRow(item: ObservableMap<TodoItem>): HorizontalLayout = HorizontalLayout().apply {
        setWidthFull()
        alignItems = FlexComponent.Alignment.CENTER
        isPadding = false
        isSpacing = true

        val checkbox = Checkbox().apply {
            checked(item[TodoItem::done])
        }

        val text = Span().apply {
            text(item[TodoItem::text])
            element.style.set("flex-grow", "1")

            autoRun("TodoRow.style") {
                val done = item[TodoItem::done].value
                style.set("text-decoration", if (done) "line-through" else "none")
                style.set("color", if (done) "var(--lumo-secondary-text-color)" else "")
            }
        }

        val remove = Button("✕") { store.removeItem(item) }
        remove.element.setAttribute("theme", "tertiary small")

        add(checkbox, text, remove)
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

        val status = Span().apply { text(store.statusText) }
        add(status)

        val spacer = Span().apply { element.style.set("flex-grow", "1") }
        add(spacer)

        val clear = Button("Clear completed") { store.clearCompleted() }
        add(clear)
    }
}

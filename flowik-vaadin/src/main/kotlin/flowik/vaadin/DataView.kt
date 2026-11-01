package flowik.vaadin

import com.vaadin.flow.component.HasValue
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.data.provider.HasListDataView
import com.vaadin.flow.data.provider.ListDataView
import flowik.core.Bindings
import flowik.core.Disposable
import flowik.core.Observable
import flowik.core.untracked
import java.util.function.Supplier

/**
 * Keeps the items of a data component — `Grid`, `Select`, `ComboBox`,
 * `ListBox`, `RadioButtonGroup`… — in step with [provider].
 *
 * Renderers, column value providers and item label generators run when Vaadin
 * writes its response, outside any reaction, so nothing they read is tracked.
 * The binding makes up for that in two ways:
 *
 * - [provider] is tracked: when what it reads changes, a *different* list
 *   replaces the items, and an *equal* one refreshes the rows on screen. Read
 *   anything a column depends on in [provider] — the date a "due" column is
 *   relative to — and the column follows it.
 * - every item that is itself [Observable], as an `ObservableEntity` is, is
 *   watched: a change to one of its properties refreshes just that item.
 *
 * A replacement keeps the selection — a field's value, a grid's selected rows —
 * as long as the selected items are still in the list.
 *
 * ```kotlin
 * Grid<ObservableEntity<Card>>().apply {
 *     addColumn { it[Card::front] }.setHeader("Front")
 *     items(store.visibleCards)
 * }
 * Select<ObservableEntity<Deck>>().apply {
 *     setItemLabelGenerator { it[Deck::name] }   // follows a rename
 *     items { store.decks.items }
 * }
 * ```
 */
context(bindings: Bindings)
fun <T, V : ListDataView<T, *>> HasListDataView<T, V>.items(provider: Supplier<List<T>>) {
    var current: List<T>? = null
    var watches: List<Disposable> = emptyList()
    bindings.register(object : Disposable {
        override fun dispose() {
            watches.forEach { it.dispose() }
            watches = emptyList()
        }
    })

    bindings.autoRun("HasListDataView.items") {
        val items = provider.get()
        untracked {
            if (items == current) {
                listDataView.refreshAll()
            } else {
                current = items
                val view = keepingSelection(items) { setItems(items) }
                watches.forEach { it.dispose() }
                watches = items.filterIsInstance<Observable>().map { observable ->
                    @Suppress("UNCHECKED_CAST")
                    observable.subscribe { view.refreshItem(observable as T) }
                }
            }
        }
    }
}

/**
 * Runs [replace], then puts back whatever of the previous selection is still
 * among [items] — `setItems` clears it in most components.
 */
private fun <T, R> HasListDataView<T, *>.keepingSelection(items: List<T>, replace: () -> R): R {
    val component = this
    if (component is Grid<*>) {
        @Suppress("UNCHECKED_CAST")
        val grid = component as Grid<T>
        val selected = grid.selectedItems.filter { it in items }
        return replace().also { selected.forEach(grid::select) }
    }

    @Suppress("UNCHECKED_CAST")
    val field = component as? HasValue<*, Any?> ?: return replace()
    val selected = field.value
    val present: Set<Any?> = items.toHashSet()
    return replace().also {
        val kept = when (selected) {
            null -> null
            is Set<*> -> selected.filterTo(LinkedHashSet()) { it in present }
            else -> selected.takeIf { it in present }
        }
        if (kept != null && field.value != kept) field.value = kept
    }
}

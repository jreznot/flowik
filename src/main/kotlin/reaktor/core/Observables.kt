package reaktor.core

/**
 * An [ObservableItems] that automatically wraps each added [T] in an
 * [Observable], so callers work with plain data values while the list
 * stores and exposes reactive [Observable]<[T]> wrappers.
 *
 * Example:
 * ```kotlin
 * data class TodoItem(val text: String, val done: Boolean = false)
 *
 * val todos = Observables<TodoItem>()
 * todos.add(TodoItem("Buy milk"))          // plain value, auto-wrapped
 *
 * val item: Observable<TodoItem> = todos[0]
 * item[TodoItem::done].value = true        // fine-grained reactive update
 * ```
 */
class Observables<T : Any>(initial: List<T> = emptyList())
    : ObservableItems<Observable<T>>() {

    init {
        initial.forEach { super.add(Observable(it)) }
    }

    /** Wraps [item] in an [Observable] and appends it to the list. */
    @JvmName("addItem")
    fun add(item: T): Unit = super.add(Observable(item))

    /** Wraps [item] in an [Observable] and inserts it at [index]. */
    @JvmName("addItemAt")
    fun add(index: Int, item: T): Unit = super.add(index, Observable(item))

    /**
     * Removes the first wrapper whose initial value equals [item].
     *
     * Relies on [Observable.equals], which compares by the wrapped
     * initial value, so the temporary sentinel wrapper is never stored.
     */
    @JvmName("removeItem")
    fun remove(item: T): Boolean = super.remove(Observable(item))

    @JvmName("setAllItems")
    fun setAll(items: List<T>) = action {
        clear()
        items.forEach { super.add(Observable(it)) }
    }
}

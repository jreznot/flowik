package reaktor.core

/**
 * An [ObservableList] that automatically wraps each added [T] in an
 * [ObservableObject], so callers work with plain data values while the list
 * stores and exposes reactive [ObservableObject]<[T]> wrappers.
 *
 * Example:
 * ```kotlin
 * data class TodoItem(val text: String, val done: Boolean = false)
 *
 * val todos = ObservableObjectList<TodoItem>()
 * todos.add(TodoItem("Buy milk"))          // plain value, auto-wrapped
 *
 * val item: ObservableObject<TodoItem> = todos[0]
 * item[TodoItem::done].value = true        // fine-grained reactive update
 * ```
 */
class ObservableObjectList<T : Any>(initial: List<T> = emptyList())
    : ObservableList<ObservableObject<T>>() {

    init {
        initial.forEach { super.add(ObservableObject(it)) }
    }

    /** Wraps [item] in an [ObservableObject] and appends it to the list. */
    @JvmName("addItem")
    fun add(item: T): Unit = super.add(ObservableObject(item))

    /** Wraps [item] in an [ObservableObject] and inserts it at [index]. */
    @JvmName("addItemAt")
    fun add(index: Int, item: T): Unit = super.add(index, ObservableObject(item))

    /**
     * Removes the first wrapper whose initial value equals [item].
     *
     * Relies on [ObservableObject.equals], which compares by the wrapped
     * initial value, so the temporary sentinel wrapper is never stored.
     */
    @JvmName("removeItem")
    fun remove(item: T): Boolean = super.remove(ObservableObject(item))
}

package flowik.core

/**
 * An [ObservableList] that automatically wraps each added [T] in an
 * [ObservableEntity], so callers work with plain data values while the list
 * stores and exposes reactive [ObservableEntity]<[T]> wrappers.
 *
 * Example:
 * ```kotlin
 * data class TodoItem(val text: String, val done: Boolean = false)
 *
 * val todos = ObservableMapList<TodoItem>()
 * todos.add(TodoItem("Buy milk"))          // plain value, auto-wrapped
 *
 * val item: ObservableMap<TodoItem> = todos[0]
 * item[TodoItem::done].value = true        // fine-grained reactive update
 * ```
 *
 * A change *inside* an element reaches this list's [subscribe] listeners, so a
 * deep tree built with [ObservableEntity.nestedList] propagates changes all the way
 * to its root. It deliberately does not invalidate readers of the list contents:
 * a reaction that must re-run on an element property reads that property.
 */
class ObservableEntityList<T : Any>(initial: List<T> = emptyList()) : ObservableList<ObservableEntity<T>>() {

    /**
     * One entry per element *occurrence*, keyed by identity — the same wrapper can
     * legitimately sit at two indices, and [ObservableEntity.equals] compares wrapped
     * values, so it cannot tell those occurrences apart.
     */
    private val elementSubscriptions = mutableListOf<Pair<ObservableEntity<T>, Disposable>>()

    init {
        // Registered before the initial items are added, so every element is watched.
        onChange { change ->
            when (change) {
                is ListChange.Insert -> watch(change.item)
                is ListChange.Remove -> unwatch(change.item)
                is ListChange.Update -> {
                    unwatch(change.old)
                    watch(change.new)
                }
                is ListChange.Clear -> change.old.forEach { unwatch(it) }
            }
        }
        initial.forEach { super.add(ObservableEntity(it)) }
    }

    /** Wraps [item] in an [ObservableEntity] and appends it to the list. */
    @JvmName("addItem")
    fun add(item: T): Unit = super.add(ObservableEntity(item))

    /** Wraps [item] in an [ObservableEntity] and inserts it at [index]. */
    @JvmName("addItemAt")
    fun add(index: Int, item: T): Unit = super.add(index, ObservableEntity(item))

    /**
     * Removes the first wrapper whose initial value equals [item].
     *
     * Relies on [ObservableEntity.equals], which compares by the wrapped
     * initial value, so the temporary sentinel wrapper is never stored.
     */
    @JvmName("removeItem")
    fun remove(item: T): Boolean = super.remove(ObservableEntity(item))

    @JvmName("setAllItems")
    fun setAll(items: List<T>) = action {
        clear()
        items.forEach { super.add(ObservableEntity(it)) }
    }

    /** Forwards an element's own changes to this list's subscribers. */
    private fun watch(item: ObservableEntity<T>) {
        elementSubscriptions.add(item to item.subscribe { notifySubscribers() })
    }

    /** Drops one subscription for [item] — the occurrence that just left the list. */
    private fun unwatch(item: ObservableEntity<T>) {
        val index = elementSubscriptions.indexOfFirst { (element, _) -> element === item }
        if (index >= 0) elementSubscriptions.removeAt(index).second.dispose()
    }
}

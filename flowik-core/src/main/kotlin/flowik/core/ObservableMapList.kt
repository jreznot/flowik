package flowik.core

/**
 * An [ObservableList] that automatically wraps each added [T] in an
 * [ObservableMap], so callers work with plain data values while the list
 * stores and exposes reactive [ObservableMap]<[T]> wrappers.
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
 * deep tree built with [ObservableMap.nestedList] propagates changes all the way
 * to its root. It deliberately does not invalidate readers of the list contents:
 * a reaction that must re-run on an element property reads that property, the
 * way the [ObservableListOps] transforms do.
 */
class ObservableMapList<T : Any>(initial: List<T> = emptyList()) : ObservableList<ObservableMap<T>>() {

    /**
     * One entry per element *occurrence*, keyed by identity — the same wrapper can
     * legitimately sit at two indices, and [ObservableMap.equals] compares wrapped
     * values, so it cannot tell those occurrences apart.
     */
    private val elementSubscriptions = mutableListOf<Pair<ObservableMap<T>, Disposable>>()

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
        initial.forEach { super.add(ObservableMap(it)) }
    }

    /** Wraps [item] in an [ObservableMap] and appends it to the list. */
    @JvmName("addItem")
    fun add(item: T): Unit = super.add(ObservableMap(item))

    /** Wraps [item] in an [ObservableMap] and inserts it at [index]. */
    @JvmName("addItemAt")
    fun add(index: Int, item: T): Unit = super.add(index, ObservableMap(item))

    /**
     * Removes the first wrapper whose initial value equals [item].
     *
     * Relies on [ObservableMap.equals], which compares by the wrapped
     * initial value, so the temporary sentinel wrapper is never stored.
     */
    @JvmName("removeItem")
    fun remove(item: T): Boolean = super.remove(ObservableMap(item))

    @JvmName("setAllItems")
    fun setAll(items: List<T>) = action {
        clear()
        items.forEach { super.add(ObservableMap(it)) }
    }

    /** Forwards an element's own changes to this list's subscribers. */
    private fun watch(item: ObservableMap<T>) {
        elementSubscriptions.add(item to item.subscribe { notifySubscribers() })
    }

    /** Drops one subscription for [item] — the occurrence that just left the list. */
    private fun unwatch(item: ObservableMap<T>) {
        val index = elementSubscriptions.indexOfFirst { (element, _) -> element === item }
        if (index >= 0) elementSubscriptions.removeAt(index).second.dispose()
    }
}

typealias ObservableMaps<T> = List<ObservableMap<T>>
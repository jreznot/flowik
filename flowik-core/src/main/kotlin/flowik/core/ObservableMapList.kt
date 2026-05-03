package flowik.core

import java.util.function.Supplier
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
 */
class ObservableMapList<T : Any>(initial: List<T> = emptyList()) : ObservableList<ObservableMap<T>>(),
    ReadWriteProperty<Any?, MutableList<T>>, Supplier<List<ObservableMap<T>>> {

    init {
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

    /**
     * A [MutableList] view that delegates mutating operations back to this
     * [ObservableMapList] instance, keeping reactive tracking intact.
     */
    private val delegate: MutableList<T> by lazy { DelegateList() }

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): MutableList<T> {
        return delegate
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: MutableList<T>) {
        setAll(value)
    }

    /**
     * Mutable list implementation that delegates add/remove/clear/size and
     * other read operations to the owning [ObservableMapList] instance.
     */
    private inner class DelegateList : AbstractMutableList<T>() {
        override val size: Int
            get() = this@ObservableMapList.size

        override fun get(index: Int): T = this@ObservableMapList[index].value

        override fun add(index: Int, element: T) {
            this@ObservableMapList.add(index, element)
        }

        override fun removeAt(index: Int): T {
            val removed = this@ObservableMapList.removeAt(index)
            return removed.value
        }

        override fun set(index: Int, element: T): T {
            val old = this@ObservableMapList[index].value
            this@ObservableMapList[index] = ObservableMap(element)
            return old
        }

        override fun clear() {
            this@ObservableMapList.clear()
        }
    }
}

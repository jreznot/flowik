package flowik.core

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
class Observables<T : Any>(initial: List<T> = emptyList()) : ObservableItems<Observable<T>>(),
    ReadWriteProperty<Any?, MutableList<T>> {

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

    /**
     * A [MutableList] view that delegates mutating operations back to this
     * [Observables] instance, keeping reactive tracking intact.
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
     * other read operations to the owning [Observables] instance.
     */
    private inner class DelegateList : AbstractMutableList<T>() {
        override val size: Int
            get() = this@Observables.size

        override fun get(index: Int): T = this@Observables[index].value

        override fun add(index: Int, element: T) {
            this@Observables.add(index, element)
        }

        override fun removeAt(index: Int): T {
            val removed = this@Observables.removeAt(index)
            return removed.value
        }

        override fun set(index: Int, element: T): T {
            val old = this@Observables[index].value
            this@Observables[index] = Observable(element)
            return old
        }

        override fun clear() {
            this@Observables.clear()
        }
    }
}

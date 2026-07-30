package flowik.core.viewmodel

import flowik.core.Disposable
import flowik.core.MutableObservable
import flowik.core.Observable
import flowik.core.ObservableEntity
import flowik.core.ObservableSet
import flowik.core.Observer
import flowik.core.action
import kotlin.reflect.KProperty1

/**
 * A buffered, editable view over an observable model — the core equivalent of
 * mobx-utils' `createViewModel`.
 *
 * Every property is proxied: as long as it has not been edited the view model
 * *reads through* to the model, so a change to the model is still visible. The
 * first write buffers the new value locally and marks the property — and the
 * view model — dirty; from then on reads return the buffered value and the model
 * is left untouched. [submit] flushes the buffer into the model, [reset] throws
 * it away. The edit is therefore a transaction: nothing reaches the model until
 * it is committed, and abandoning it restores the previous state for free.
 *
 * ```kotlin
 * data class FormData(val name: String = "<Unnamed>", val email: String = "")
 *
 * val model = observable(FormData())          // ObservableEntity<FormData>
 * val form = createViewModel(model)
 *
 * autoRun { println("form=${form[FormData::name]} model=${model[FormData::name]} dirty=${form.isDirty}") }
 *
 * form[FormData::name] = "Pavan"              // form=Pavan  model=<Unnamed>  dirty=true
 * form.reset()                                // form=<Unnamed> model=<Unnamed> dirty=false
 * form[FormData::name] = "Flowik"
 * form.submit()                               // form=Flowik model=Flowik dirty=false
 * ```
 *
 * ### Which model
 *
 * Two flavours, picked by the overload of [createViewModel] that was called:
 *
 * - [EntityViewModel] over an [flowik.core.ObservableEntity] — the usual way to edit a data
 *   class, and what `createViewModel(observable(dto))` returns.
 * - [StoreViewModel] over a store object holding atoms of its own
 *   (`var name by observable("")` or `val name = observable("")`).
 *
 * They differ only in where the atoms come from, and in the type of [model].
 *
 * ### Reactivity
 *
 * Every read auto-tracks, at the granularity of a single property: a reaction
 * that reads `form[FormData::name]` re-runs when that property is edited, reset,
 * or submitted — and, while the property is clean, when the *model* changes
 * underneath. It does not re-run for an edit to a sibling property. [isDirty]
 * and [changedValues] are aggregate, so they re-run for any property.
 *
 * [property] hands out the buffered atom itself, a [flowik.core.MutableObservable] that
 * drives the same two-way UI bindings the model atoms do — which is the point of
 * an OK/Cancel dialog: bind the form to the view model, call [submit] on OK and
 * nothing at all on Cancel.
 *
 * ### Scalar atoms only
 *
 * A view model buffers values, so it covers properties that hold one. Reactive
 * collections ([flowik.core.ObservableList], [flowik.core.ObservableSet]) are not buffered — a buffer
 * would hand out a live view of the model's own contents, and writes through it
 * would bypass [submit] entirely. Accessing one is an error that says so; edit it
 * directly, or keep the edited copy in an atom of its own.
 */
sealed class ViewModel<T : Any> : Observable {

    /**
     * The model being edited — narrowed to [flowik.core.ObservableEntity]<[T]> by
     * [EntityViewModel] and to [T] by [StoreViewModel].
     */
    abstract val model: Any

    /** One buffered proxy per property the view model has been asked for. */
    private val proxies = mutableMapOf<String, ViewModelProperty<Any?>>()

    /**
     * The names of the properties currently holding an edit.
     *
     * Aggregate queries ([isDirty], [changedValues]) track this one container
     * rather than every proxy, so they stay correct for properties that are
     * accessed for the first time later on.
     */
    private val dirtyNames = ObservableSet<String>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    /** Disposables for the internal subscriptions to the proxies. */
    private val childSubscriptions = mutableMapOf<String, Disposable>()

    // Reading and writing

    /**
     * Reads a property — `form[FormData::name]` — buffered value if it was
     * edited, the model's value otherwise. Reading auto-tracks.
     */
    operator fun <P> get(prop: KProperty1<T, P>): P = property(prop).value

    /**
     * Buffers a write — `form[FormData::name] = "…"`. The model is not touched
     * until [submit].
     */
    operator fun <P> set(prop: KProperty1<T, P>, value: P) {
        property(prop).value = value
    }

    /**
     * Returns the buffered atom for a property, created on first access and
     * cached afterwards. Bind a UI component to it to edit through the buffer.
     *
     * @throws NoSuchElementException   if the model has no such property.
     * @throws IllegalArgumentException if the property cannot be buffered — it is
     *         a reactive collection, or (for a [StoreViewModel]) not an atom at all.
     */
    fun <P> property(prop: KProperty1<T, P>): ViewModelProperty<P> = get(prop.name)

    /**
     * Returns the buffered atom for the property named [name] — the untyped
     * counterpart of [property].
     *
     * @throws NoSuchElementException   if the model has no such property.
     * @throws IllegalArgumentException if the property cannot be buffered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> get(name: String): ViewModelProperty<P> = proxy(name) as ViewModelProperty<P>

    // Transaction state

    /** `true` while any property holds an edit that has not been submitted. Auto-tracks. */
    val isDirty: Boolean get() = !dirtyNames.isEmpty()

    /**
     * The buffered edits, keyed by property name, in the order the properties
     * were edited — what [submit] is about to write. Auto-tracks.
     */
    val changedValues: Map<String, Any?>
        get() = dirtyNames.items.associateWith { name -> proxies.getValue(name).value }

    /** Whether [prop] holds an edit. Auto-tracks. */
    fun isPropertyDirty(prop: KProperty1<T, *>): Boolean = isPropertyDirty(prop.name)

    /**
     * Whether the property named [name] holds an edit. Auto-tracks.
     *
     * Unlike [get] this does not materialise the proxy: a property nobody has
     * touched yet is clean, and the answer is tracked against [dirtyNames] so a
     * later first edit still re-runs the reader.
     */
    fun isPropertyDirty(name: String): Boolean = proxies[name]?.isDirty ?: (name in dirtyNames)

    // Committing and reverting

    /**
     * Writes every buffered edit into the model and drops the buffer, leaving the
     * view model clean. One [flowik.core.action], so dependents of the model re-run once.
     */
    fun submit(): Unit = action {
        proxies.values.toList().forEach { it.submit() }
    }

    /**
     * Discards every buffered edit — the view model reads through to the model
     * again, which never saw the edits. One [action].
     */
    fun reset(): Unit = action {
        proxies.values.toList().forEach { it.reset() }
    }

    /** Discards the edit buffered for [prop], leaving the other properties alone. */
    fun resetProperty(prop: KProperty1<T, *>): Unit = resetProperty(prop.name)

    /** Discards the edit buffered for the property named [name]. */
    fun resetProperty(name: String) {
        proxies[name]?.reset()
    }

    /**
     * Notified whenever an edit is buffered, submitted or reset — and, for a
     * property that is currently clean, when the model itself changes.
     */
    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    override fun toString(): String = "ViewModel($model, dirty=${dirtyNames.items})"

    /**
     * Resolves the model's own atom for the property named [name] — the thing the
     * buffer reads through to and submits into.
     */
    protected abstract fun atom(name: String): MutableObservable<Any?>

    private fun proxy(name: String): ViewModelProperty<Any?> = proxies.getOrPut(name) {
        val created = BufferedProperty(name, atom(name), dirtyNames)
        childSubscriptions[name] = created.subscribe { notifySubscribers() }
        created
    }

    private fun notifySubscribers() {
        subscribers.toList().forEach { it.onChange() }
    }
}

/**
 * Wraps [model] in an [EntityViewModel], so a data class can be edited as a
 * transaction: reads fall through to [model] until they are written, [submit]
 * commits, [reset] reverts.
 *
 * The properties are exposed as scalar atoms, which is the one access pattern an
 * [flowik.core.ObservableEntity] allows per property — a property already reached through
 * `nested` / `nestedList` cannot also be edited through a view model, and
 * decomposing one that the view model has taken fails the same way.
 */
fun <T : Any> createViewModel(model: ObservableEntity<T>): EntityViewModel<T> = EntityViewModel(model)

/**
 * Wraps a store object in a [StoreViewModel], buffering the atoms it holds —
 * `var name by observable("")` and `val name = observable("")` alike.
 *
 * For a plain object with no atoms of its own, wrap it first:
 * `createViewModel(observable(dto))`.
 */
fun <T : Any> createViewModel(model: T): StoreViewModel<T> = StoreViewModel(model)

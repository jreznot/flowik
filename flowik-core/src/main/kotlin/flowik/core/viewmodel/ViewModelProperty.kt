package flowik.core.viewmodel

import flowik.core.Disposable
import flowik.core.MutableObservable
import flowik.core.ObservableSet
import flowik.core.ObservableValue
import flowik.core.Observer
import flowik.core.action
import flowik.core.untracked
import kotlin.reflect.KProperty

/**
 * One property of a [ViewModel]: an atom that reads through to the model until
 * it is written, and buffers the edit afterwards.
 *
 * It is a [flowik.core.MutableObservable], so it drives the same two-way bindings as the
 * model's own atoms and works as a property delegate:
 *
 * ```kotlin
 * textField().bindText(form.property(FormData::name))
 * var draft: String by form.property(FormData::name)
 * ```
 *
 * [isDirty] and [reset] are the single-property counterparts of
 * [ViewModel.isDirty] and [ViewModel.reset].
 */
interface ViewModelProperty<P> : MutableObservable<P> {

    /** The name of the model property this buffers. */
    val name: String

    /** Whether this property holds an edit that has not been submitted. Auto-tracks. */
    val isDirty: Boolean

    /** Writes the buffered edit — if any — into the model, leaving this property clean. */
    fun submit()

    /** Discards the buffered edit — if any — so reads fall through to the model again. */
    fun reset()
}

/**
 * The [ViewModelProperty] implementation: a dirty flag plus a buffer, in front of
 * the model's own atom.
 *
 * Reads track the flag and exactly one of the two values — the buffer while
 * dirty, the [source] while clean — which is what makes a clean property follow
 * the model and a dirty one ignore it.
 *
 * [dirtyNames] is the owning view model's aggregate registry, kept in step here
 * so that [ViewModel.isDirty] does not have to poll every proxy.
 */
internal class BufferedProperty<P>(
    override val name: String,
    private val source: MutableObservable<P>,
    private val dirtyNames: ObservableSet<String>,
) : ViewModelProperty<P> {

    private val dirty = ObservableValue(false, name = "$name-dirty")

    /** The buffered edit. Meaningful only while [dirty] — `null` otherwise. */
    private val buffer = ObservableValue<P?>(null, name = "$name-buffer")

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    init {
        // Notified for as long as this proxy exists, so a subscriber registered
        // later still sees the model move underneath a clean property.
        source.subscribe { if (!dirty.untrackedValue) notifySubscribers() }
    }

    override var value: P
        get() {
            @Suppress("UNCHECKED_CAST")
            return if (dirty.value) buffer.value as P else source.value
        }
        set(new) {
            // Writing the value the model already has leaves a clean property clean —
            // there is nothing to submit. A property that is already dirty stays dirty
            // even then, as in mobx-utils: reverting is what reset() is for.
            val wasDirty = dirty.untrackedValue
            if (new == if (wasDirty) buffer.untrackedValue else untracked { source.value }) return
            action {
                buffer.value = new
                if (!wasDirty) {
                    dirty.value = true
                    dirtyNames.add(name)
                }
            }
            notifySubscribers()
        }

    override val isDirty: Boolean get() = dirty.value

    override fun submit() {
        if (!dirty.untrackedValue) return
        action {
            @Suppress("UNCHECKED_CAST")
            source.value = buffer.untrackedValue as P
            clear()
        }
        notifySubscribers()
    }

    override fun reset() {
        if (!dirty.untrackedValue) return
        action { clear() }
        notifySubscribers()
    }

    /**
     * Notified once per edit, submit and reset — and, while this property is
     * clean, whenever the model's own atom changes. That is exactly when [value]
     * can return something else.
     */
    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): P = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: P) {
        this.value = value
    }

    override fun get(): P = value

    override fun toString(): String =
        "ViewModelProperty($name=${if (dirty.untrackedValue) buffer.untrackedValue else "<model>"})"

    private fun notifySubscribers() {
        subscribers.toList().forEach { it.onChange() }
    }

    /** Drops the edit. Called inside an [action] — the two writes are one change. */
    private fun clear() {
        dirty.value = false
        buffer.value = null
        dirtyNames.remove(name)
    }
}

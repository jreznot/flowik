@file:Suppress("UnstableApiUsage")

package flowik.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import flowik.core.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JToggleButton
import javax.swing.text.JTextComponent
import kotlin.reflect.KProperty0

/**
 * Exposes a read-only Flowik observable (`ObservableValue`, `Computed`, ...) as
 * an [ObservableProperty], usable with `Cell<JLabel>.bindText`,
 * `visibleIf`, `enabledIf`, `Cell.comment`-style one-way bindings.
 */
fun <T> ReadableObservable<T>.asProperty(bindings: Bindings): ObservableProperty<T> =
    FlowikProperty(this, bindings)

/**
 * Exposes a writable Flowik observable as an [ObservableMutableProperty], which
 * unlocks every `bindXxx(ObservableMutableProperty)` overload of the UI DSL.
 *
 * The binding is **live**: the component writes into the store on every
 * keystroke/click, and store changes reach the component immediately. That is
 * what a tool window wants; for OK/Apply semantics use [asMutableProperty].
 *
 * Writes are wrapped in [action], so a single UI event produces one batch.
 */
fun <T> MutableObservable<T>.asProperty(bindings: Bindings): ObservableMutableProperty<T> =
    FlowikProperty(this, bindings)

/**
 * Same, for a property delegated to an observable — `store::name` instead of
 * `store.name`. Mirrors `flowik.core.unwrapBinding` used by the Swing/Vaadin
 * bindings.
 */
fun <T> KProperty0<T>.asProperty(bindings: Bindings): ObservableMutableProperty<T> =
    unwrapBinding(this).asProperty(bindings)

/**
 * One implementation backs both factories: read-only sources are simply handed
 * out under the [ObservableProperty] type, so [set] is unreachable for them.
 *
 * Notes on the mapping:
 *  - a *reaction* (not an autoRun) drives the change events: it must not fire on
 *    creation, and it hands over the freshly supplied value;
 *  - [get] is untracked on purpose. The platform reads properties from arbitrary
 *    places — including from inside a Flowik derivation, e.g. while an
 *    `autoRun` is rebuilding a panel — and those reads must not silently become
 *    dependencies of that derivation;
 *  - events are de-duplicated by `equals`, which keeps redundant `setText` calls
 *    (and caret jumps) away from bound editors;
 *  - the event is fired synchronously when already on the EDT. That matters:
 *    the platform's own `JTextComponent.bind` breaks the write-read loop with a
 *    mutex held *for the duration of `set`*, so a deferred event would slip past
 *    the guard and re-set the text under the user's caret.
 */
private class FlowikProperty<T>(
    private val source: ReadableObservable<T>,
    bindings: Bindings
) : ObservableMutableProperty<T> {

    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    @Volatile
    private var lastFired: T = untracked { source.value }

    init {
        val subscription = reaction(
            name = "flowik -> ObservableProperty($source)",
            supply = { source.value },
            effect = { value -> fireChangeEvent(value) },
        )
        bindings.register(subscription)
    }

    override fun get(): T = untracked { source.value }

    override fun set(value: T) {
        val mutable = source as? MutableObservable<T>
            ?: throw UnsupportedOperationException("$source is read-only")
        action { mutable.value = value }
    }

    override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) {
        listeners += listener
        parentDisposable?.onDispose { listeners -= listener }
    }

    private fun fireChangeEvent(value: T) {
        if (value == lastFired) return
        lastFired = value
        listeners.forEach { it(value) }
    }

    override fun toString(): String = "FlowikProperty($source)"
}

context(bindings: Bindings)
fun <T : JTextComponent> Cell<T>.bindText(model: MutableObservable<String>): Cell<T> =
    bindText(model.asProperty(bindings))

context(bindings: Bindings)
fun <T : JTextComponent> Cell<T>.bindText(prop: KProperty0<String>): Cell<T> =
    bindText(prop.asProperty(bindings))

context(bindings: Bindings)
fun <T : JTextComponent> Cell<T>.bindIntText(model: MutableObservable<Int>): Cell<T> =
    bindIntText(model.asProperty(bindings))

context(bindings: Bindings)
fun <T : JToggleButton> Cell<T>.bindSelected(model: MutableObservable<Boolean>): Cell<T> =
    bindSelected(model.asProperty(bindings))

context(bindings: Bindings)
fun <T : JToggleButton> Cell<T>.bindSelected(model: KProperty0<Boolean>): Cell<T> =
    bindSelected(model.asProperty(bindings))

context(bindings: Bindings)
fun <T, C : ComboBox<T>> Cell<C>.bindItem(model: MutableObservable<T>): Cell<C> =
    bindItem(model.asProperty(bindings))

context(bindings: Bindings)
fun <T : JLabel> Cell<T>.bindText(model: ReadableObservable<String>): Cell<T> =
    bindText(model.asProperty(bindings))

/**
 * Derives the text from an arbitrary expression: `label("").text { "${a.value} / ${b.value}" }`.
 * No intermediate `computed` to declare, dependencies wire themselves.
 */
context(bindings: Bindings)
fun <T : JLabel> Cell<T>.text(expression: () -> String): Cell<T> =
    bindText(computed(expression))

// --- structure: visibility / enabled state ------------------------------
//
// `computedStruct` rather than `computed`: a plain computed propagates
// *invalidation*, so every upstream write would re-fire the predicate and
// re-apply visibility (each one a layout pass). computedStruct fires only on
// an actual false <-> true transition. The derivation is disposed with
// `parent` by the adapter.

context(bindings: Bindings)
fun <C : JComponent> Cell<C>.visibleIf(condition: () -> Boolean): Cell<C> {
    val computedDisposable = computedStruct(condition)
    bindings.register(computedDisposable)
    return visibleIf(computedDisposable.asProperty(bindings))
}

context(bindings: Bindings)
fun <C : JComponent> Cell<C>.enabledIf(condition: () -> Boolean): Cell<C> {
    val computedDisposable = computedStruct(condition)
    bindings.register(computedDisposable)
    return enabledIf(computedDisposable.asProperty(bindings))
}

context(bindings: Bindings)
fun Row.visibleIf(condition: () -> Boolean): Row {
    val computedDisposable = computedStruct(condition)
    bindings.register(computedDisposable)
    return visibleIf(computedDisposable.asProperty(bindings))
}

context(bindings: Bindings)
fun Row.enabledIf(condition: () -> Boolean): Row {
    val computedDisposable = computedStruct(condition)
    bindings.register(computedDisposable)
    return enabledIf(computedDisposable.asProperty(bindings))
}

/**
 * Binds any component aspect the UI DSL has no property for — icon,
 * foreground, tooltip, empty text, a whole custom component's state.
 *
 * [read] is *tracked* (its observable reads become the dependencies) and
 * runs on whichever thread wrote the store; [update] receives the value on
 * the EDT. Splitting the two is what makes this thread-safe — see [autoRun]
 * for the shorter, EDT-only form.
 *
 * ```
 * icon(AllIcons.General.Warning).bindIn({ store.errors.size }) { isVisible = it > 0 }
 * ```
 */
context(bindings: Bindings)
fun <C : JComponent, V> Cell<C>.bindIn(read: () -> V, update: C.(V) -> Unit): Cell<C> = also {
    component.update(untracked(read))
    val subscription = reaction(
        name = "Cell.bindIn(${component.javaClass.simpleName})",
        supply = read,
        effect = { value -> component.update(value) },
    )
    bindings.register(subscription)
}

/**
 * The one-lambda variant: reads and writes in the same block, exactly like a Flowik `autoRun`.
 *
 * ```
 * cell(myChart).autoRun { model = store.series.value; repaint() }
 * ```
 *
 * Precondition: the store must be mutated on the EDT (which
 * `flowik.core.runInAction` guarantees). The effect body cannot be deferred
 * to the EDT without leaving the tracking scope — the dependencies would be
 * lost — so it runs on the writing thread. If background mutation is a
 * possibility, use [bindIn].
 */
context(bindings: Bindings)
fun <C : JComponent> Cell<C>.autoRun(name: String? = null, effect: C.() -> Unit): Cell<C> = also {
    val subscription = flowik.core.autoRun(name ?: "Cell.autoRun(${component.javaClass.simpleName})") {
        component.effect()
    }
    bindings.register(subscription)
}

/**
 * Rebuilds the content of a [Placeholder] whenever the observables read by
 * [content] change: the UI DSL equivalent of Flowik's `ForEach`/`SwitchPanel`.
 *
 * Each rebuild gets a fresh child [Disposable], so the bindings of the
 * discarded sub-panel are released instead of piling up on `parent` — the
 * mistake that makes naive "rebuild in an autoRun" implementations leak.
 * `FlowikProperty.get()` being untracked is what keeps the *nested* bindings
 * from registering themselves as dependencies of this rebuild.
 *
 * ```
 * lateinit var body: Placeholder
 * row { body = placeholder() }
 * body.bindContent {
 *     panel {
 *         for (item in store.items) {
 *             row { label("").bindText(item[Item::title]) }
 *         }
 *     }
 * }
 * ```
 *
 * Same EDT precondition as [autoRun] — it mutates the layout in place.
 */
context(bindings: Bindings)
fun Placeholder.bindContent(name: String? = null, content: () -> JComponent?): Placeholder = apply {
    val subscription = autoRun(name ?: "Placeholder.bindContent") {
        component = content()
    }
    bindings.register(subscription)
}

/**
 * Exposes an observable as a UI DSL [MutableProperty], i.e. the *deferred*
 * contract: the component is filled from the store on `DialogPanel.reset()`,
 * the store is written on `DialogPanel.apply()`, and `isModified()` compares the
 * two. Nothing reaches the store while the user types.
 *
 * This is the right option for a `Configurable`, where OK/Apply/Cancel must
 * mean something; [asProperty] (live two-way) is the right option for a tool
 * window or an inspector, where there is no Apply button to wait for.
 *
 * The two can be mixed in one panel: bind the persisted settings deferred, and
 * bind the ephemeral view state (filters, expanded state, previews) live.
 */
fun <T> MutableObservable<T>.asMutableProperty(): MutableProperty<T> =
    MutableProperty({ untracked { value } }, { newValue -> action { value = newValue } })

/**
 * `Disposer.register` with a lambda. Throws if [this] is already disposed, which
 * is the intended behavior: it catches bindings created against a dead panel.
 */
private fun Disposable.onDispose(action: () -> Unit) {
    Disposer.register(this) { action() }
}

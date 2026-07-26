package org.example

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
import flowik.core.Disposable as FlowikDisposable

/*
 * Bridging the IntelliJ Kotlin UI DSL and Flowik's MobX-style state.
 *
 * The two worlds are close enough that one adapter carries almost everything:
 *
 *   Flowik                                    IntelliJ platform
 *   ------------------------------------      -----------------------------------
 *   ReadableObservable<T>.value          <->  ObservableProperty<T>.get()
 *   subscribe / reaction / autoRun       <->  ObservableProperty.afterChange(...)
 *   MutableObservable<T>.value = x       <->  ObservableMutableProperty<T>.set(x)
 *   Disposable (flowik.core)             <->  Disposable (com.intellij.openapi)
 *
 * The UI DSL already has a full set of `bindXxx(ObservableMutableProperty)`
 * overloads (textField, intTextField, checkBox, comboBox, segmentedButton,
 * slider, spinner, label, ...) plus `visibleIf`/`enabledIf(ObservableProperty)`.
 * So the cheapest useful integration is *not* a new set of bindings — it is one
 * adapter (Option 1) that makes a Flowik observable look like a platform
 * property. Everything else in this file is ergonomics on top of it.
 *
 * Why route through ObservableProperty instead of driving components straight
 * from `autoRun`:
 *   - `bindText(ObservableMutableProperty)` also installs the cell's *validation
 *     requestor* (`Cell.installValidationRequestor`), so `textValidation { }`
 *     fires on every keystroke. A hand-rolled autoRun binding silently loses
 *     validation-on-input.
 *   - `visibleIf`/`enabledIf` go through the DSL's own visibility/enabled
 *     bookkeeping (labels, comments, whole rows, parent state). Setting
 *     `component.isVisible` from an autoRun fights that bookkeeping.
 *   - `DialogPanel.apply/reset/isModified` keeps working (see Option 4).
 *
 * Options, and when to pick which:
 *
 *   1. asProperty(parent)            The core adapter. Live, two-way, works with
 *                                    every existing bindXxx overload.
 *   2. bindText(model, parent), ...  Thin overloads so call sites never mention
 *                                    ObservableProperty at all.
 *   3. FlowikBindings scope          Same as 2, with the parent Disposable
 *                                    supplied once instead of per call site.
 *                                    Adds MobX-style expression bindings
 *                                    (`bindIn`, `visibleIf { }`) that no
 *                                    platform property can express.
 *   4. asMutableProperty()           Deferred (apply/reset/isModified) binding
 *                                    for Configurable / DialogWrapper panels.
 *   5. Placeholder.bindContent { }   Reactive *structure*, not just values.
 *
 * Two cross-cutting rules, both consequences of Flowik's design (notifications
 * are synchronous, on the writing thread, and nothing is thread-confined):
 *
 *   Threading — every adapter here reads/derives on the writing thread and
 *   marshals the *UI update* to the EDT. Writes into the store from a background
 *   thread should still go through `flowik.core.runInAction`, which switches to
 *   `Dispatchers.Main` and batches. Never touch a store from two threads at once.
 *
 *   Lifetime — a Flowik store is usually longer-lived than the panel (project
 *   service vs. tool window). Every subscription therefore needs an explicit
 *   parent `Disposable`: `toolWindow.disposable`, `DialogWrapper.disposable`,
 *   `Configurable.disposeUIResources`, or a child from `Disposer.newDisposable`.
 *   Without it the store keeps the whole component tree alive.
 *
 * Suggested placement: a new `flowik-intellij` module with
 * `api(project(":flowik-core"))` and the IntelliJ platform as `compileOnly`, so
 * plugins get the bridge without the demo. (Note for the demo module: a plain
 * `implementation(project(":flowik-core"))` also drags coroutines/slf4j onto the
 * plugin classpath — a real module should mark those `compileOnly` and rely on
 * the platform's own copies.)
 */

/**
 * Exposes a read-only Flowik observable (`ObservableValue`, `Computed`, ...) as
 * an [ObservableProperty], usable with `Cell<JLabel>.bindText`,
 * `visibleIf`, `enabledIf`, `Cell.comment`-style one-way bindings.
 *
 * The subscription — and, for a [FlowikDisposable] source such as
 * `computedStruct`, the derivation itself — is released with [parent].
 */
fun <T> ReadableObservable<T>.asProperty(parent: Disposable): ObservableProperty<T> =
    FlowikProperty(this, parent)

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
fun <T> MutableObservable<T>.asProperty(parent: Disposable): ObservableMutableProperty<T> =
    FlowikProperty(this, parent)

/**
 * Same, for a property delegated to an observable — `store::name` instead of
 * `store.name`. Mirrors `flowik.core.unwrapBinding` used by the Swing/Vaadin
 * bindings.
 */
fun <T> KProperty0<T>.asProperty(parent: Disposable): ObservableMutableProperty<T> =
    unwrapBinding(this).asProperty(parent)

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
    parent: Disposable,
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
        parent.onDispose {
            subscription.dispose()
            (source as? FlowikDisposable)?.dispose()
            listeners.clear()
        }
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

fun <T : JTextComponent> Cell<T>.bindText(model: MutableObservable<String>, parent: Disposable): Cell<T> =
    bindText(model.asProperty(parent))

fun <T : JTextComponent> Cell<T>.bindIntText(model: MutableObservable<Int>, parent: Disposable): Cell<T> =
    bindIntText(model.asProperty(parent))

fun <T : JLabel> Cell<T>.bindText(model: ReadableObservable<String>, parent: Disposable): Cell<T> =
    bindText(model.asProperty(parent))

fun <T : JToggleButton> Cell<T>.bindSelected(model: MutableObservable<Boolean>, parent: Disposable): Cell<T> =
    bindSelected(model.asProperty(parent))

fun <T, C : ComboBox<T>> Cell<C>.bindItem(model: MutableObservable<T>, parent: Disposable): Cell<C> =
    bindItem(model.asProperty(parent))

/**
 * Collects the bindings of one panel under a single lifetime.
 *
 * `Panel` and `Cell` are `@ApiStatus.NonExtendable`, so this is deliberately a
 * *separate* receiver rather than a `Panel` wrapper — the UI DSL stays exactly
 * itself, and the bindings ride in as outer-receiver member extensions:
 *
 * ```
 * with(FlowikBindings(toolWindow.disposable)) {
 *     panel {
 *         row("Name:") { textField().bindText(store.name) }
 *         row { label("").bindText(store.greeting) }
 *         row { button("Reset") { store.reset() } }.visibleIf { store.isDirty.value }
 *     }
 * }
 * ```
 *
 * Everything a Flowik user expects is available inside the lambdas — reads are
 * plain reads, `action { }` batches, and nothing has to be unsubscribed by hand.
 */
class FlowikBindings(private val parent: Disposable) {

    fun <T : JTextComponent> Cell<T>.bindText(model: MutableObservable<String>): Cell<T> =
        bindText(model.asProperty(parent))

    fun <T : JTextComponent> Cell<T>.bindText(prop: KProperty0<String>): Cell<T> =
        bindText(prop.asProperty(parent))

    fun <T : JTextComponent> Cell<T>.bindIntText(model: MutableObservable<Int>): Cell<T> =
        bindIntText(model.asProperty(parent))

    fun <T : JToggleButton> Cell<T>.bindSelected(model: MutableObservable<Boolean>): Cell<T> =
        bindSelected(model.asProperty(parent))

    fun <T, C : ComboBox<T>> Cell<C>.bindItem(model: MutableObservable<T>): Cell<C> =
        bindItem(model.asProperty(parent))

    fun <T : JLabel> Cell<T>.bindText(model: ReadableObservable<String>): Cell<T> =
        bindText(model.asProperty(parent))

    /**
     * Derives the text from an arbitrary expression: `label("").text { "${a.value} / ${b.value}" }`.
     * No intermediate `computed` to declare, dependencies wire themselves.
     */
    fun <T : JLabel> Cell<T>.text(expression: () -> String): Cell<T> =
        bindText(computed(expression))

    // --- structure: visibility / enabled state ------------------------------
    //
    // `computedStruct` rather than `computed`: a plain computed propagates
    // *invalidation*, so every upstream write would re-fire the predicate and
    // re-apply visibility (each one a layout pass). computedStruct fires only on
    // an actual false <-> true transition. The derivation is disposed with
    // `parent` by the adapter.

    fun <C : JComponent> Cell<C>.visibleIf(condition: () -> Boolean): Cell<C> =
        visibleIf(computedStruct(condition).asProperty(parent))

    fun <C : JComponent> Cell<C>.enabledIf(condition: () -> Boolean): Cell<C> =
        enabledIf(computedStruct(condition).asProperty(parent))

    fun Row.visibleIf(condition: () -> Boolean): Row =
        visibleIf(computedStruct(condition).asProperty(parent))

    fun Row.enabledIf(condition: () -> Boolean): Row =
        enabledIf(computedStruct(condition).asProperty(parent))

    /**
     * Binds any component aspect the UI DSL has no property for — icon,
     * foreground, tooltip, empty text, a whole custom component's state.
     *
     * [read] is *tracked* (its observable reads become the dependencies) and
     * runs on whichever thread wrote the store; [update] receives the value on
     * the EDT. Splitting the two is what makes this thread-safe — see [reactive]
     * for the shorter, EDT-only form.
     *
     * ```
     * icon(AllIcons.General.Warning).bindIn({ store.errors.size }) { isVisible = it > 0 }
     * ```
     */
    fun <C : JComponent, V> Cell<C>.bindIn(read: () -> V, update: C.(V) -> Unit): Cell<C> = also {
        component.update(untracked(read))
        val subscription = reaction(
            name = "Cell.bindIn(${component.javaClass.simpleName})",
            supply = read,
            effect = { value -> component.update(value) },
        )
        parent.onDispose { subscription.dispose() }
    }

    /**
     * The one-lambda variant: reads and writes in the same block, exactly like a
     * Flowik `autoRun`.
     *
     * ```
     * cell(myChart).reactive { model = store.series.value; repaint() }
     * ```
     *
     * Precondition: the store must be mutated on the EDT (which
     * `flowik.core.runInAction` guarantees). The effect body cannot be deferred
     * to the EDT without leaving the tracking scope — the dependencies would be
     * lost — so it runs on the writing thread. If background mutation is a
     * possibility, use [bindIn].
     */
    fun <C : JComponent> Cell<C>.reactive(name: String? = null, effect: C.() -> Unit): Cell<C> = apply {
        val subscription = autoRun(name ?: "Cell.reactive(${component.javaClass.simpleName})") {
            component.effect()
        }
        parent.onDispose { subscription.dispose() }
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
     *         for (item in store.items) row { label("").bindText(item[Item::title]) }
     *     }
     * }
     * ```
     *
     * Same EDT precondition as [reactive] — it mutates the layout in place.
     */
    fun Placeholder.bindContent(name: String? = null, content: FlowikBindings.() -> JComponent?): Placeholder = apply {
        var previous: Disposable? = null
        val subscription = autoRun(name ?: "Placeholder.bindContent") {
            previous?.let { Disposer.dispose(it) }
            val scope = Disposer.newDisposable(parent, "flowik.placeholder.content")
            previous = scope
            component = FlowikBindings(scope).content()
        }
        parent.onDispose { subscription.dispose() }
    }
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
 * is the intended behaviour: it catches bindings created against a dead panel.
 */
private fun Disposable.onDispose(action: () -> Unit) {
    Disposer.register(this) { action() }
}

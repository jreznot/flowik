# Flowik

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![team JetBrains project](https://jb.gg/badges/team.svg)](https://github.com/JetBrains#jetbrains-on-github)

Flowik is a small Kotlin library that brings **MobX-style reactive state** to JVM UI toolkits.
You define plain observable values and computed expressions; the UI subscribes itself and re-renders
automatically when anything it reads changes — no listeners, no manual rebinding.

The library is split into a framework-agnostic core and three UI-binding modules:

| Module            | Purpose                                                                 |
|-------------------|-------------------------------------------------------------------------|
| `flowik-core`     | Reactive primitives: observables, computed, autoRun, reactions, actions |
| `flowik-swing`    | Bindings for Swing components                                           |
| `flowik-vaadin`   | Bindings for Vaadin Flow components                                     |
| `flowik-intellij` | Bindings for the IntelliJ Platform [Kotlin UI DSL][ij:ui-dsl]           |

Most users only need **one** of `flowik-swing`, `flowik-vaadin` or `flowik-intellij` — all three pull
`flowik-core` in transitively.

[ij:ui-dsl]: https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html

## The MobX concept

Flowik is directly inspired by [MobX](https://mobx.js.org/). The core idea, in one sentence:

> **Anything that can be derived from the application state should be derived. Automatically.**

In practice this means three building blocks:

- **Observable state** — mutable values that *track who reads them*.
  Created with `observable(...)`, `observableEntity(...)`, `observables(...)`, `observableSet(...)`.
- **Derivations** — pure functions over observables.
  Either `computed { ... }` for cached values or `autoRun { ... }` for side effects (typically UI updates).
  Both are *self-wiring*: they re-evaluate when — and only when — something they read has changed.
- **Actions** — `action { ... }` blocks that batch writes so derivations fire once at the end.

There is no event bus, no dependency graph you maintain by hand, no `.subscribe()` boilerplate at every
call site. Read an observable from inside a derivation, and you are subscribed; stop reading it and you
are not. For background and rationale, the [MobX docs](https://mobx.js.org/the-gist-of-mobx.html) are
the best introduction — the mental model carries over directly.

## Getting it via JitPack

Releases are published through [JitPack](https://jitpack.io/#jreznot/flowik). The first time a version
is requested, JitPack builds it from the git tag — the badge page above shows the latest available tag
and triggers a build for new ones.

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // pick one of these:
    implementation("com.github.jreznot.flowik:flowik-swing:v0.3.0")
    // OR
    implementation("com.github.jreznot.flowik:flowik-vaadin:v0.3.0")
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.jreznot.flowik</groupId>
    <artifactId>flowik-swing</artifactId> <!-- or flowik-vaadin, flowik-intellij -->
    <version>v0.3.0</version>
</dependency>
```

`flowik-core` is pulled in transitively. You only need to declare it explicitly if you want the
reactive primitives without any UI bindings.

To follow the latest commit instead of a tagged release, use `main-SNAPSHOT` as the version.

## A minimal example

A typical Flowik app has two parts: a **store** (plain Kotlin class holding observables and computed
values) and a **view** that reads from the store. The view never registers listeners — it just reads,
and Flowik does the rest.

```kotlin
import flowik.core.*

class CounterStore {
    val count = observable(0)
    val doubled = computed { count.value * 2 }

    fun inc() = action { count.value += 1 }
}
```

### Using it from Swing (`flowik-swing`)

```kotlin
import flowik.core.*
import flowik.layout.uiFrame
import flowik.swing.*

fun main() {
    val store = CounterStore()
    uiFrame("Counter", width = 240, height = 120) {
        center {
            vbox(gap = 4) {
                Label { "Count: ${store.count.value} (×2 = ${store.doubled.value})" }
                Button("Increment") { store.inc() }
            }
        }
    }
}
```

The `Label` lambda reads `store.count` and `store.doubled`, so the label re-renders whenever either
changes. No explicit subscription.

### Using it from Vaadin (`flowik-vaadin`)

```kotlin
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.Route
import flowik.core.*
import flowik.vaadin.*

@Route("counter")
class CounterView : VerticalLayout() {
    private val store = CounterStore()

    init {
        val label = Span()
        label.text { "Count: ${store.count.value} (×2 = ${store.doubled.value})" }
        add(label, Button("Increment") { store.inc() })
    }
}
```

The Vaadin bindings also expose two-way property binding for inputs — for example
`TextField().apply { value(store::filter) }` keeps a `TextField` in sync with an observable string
property.

### Using it from a plugin (`flowik-intellij`)

The IntelliJ Platform has its own declarative panel builder, the [Kotlin UI DSL][ij:ui-dsl]. Flowik
does not replace it — it plugs into it, so `panel { row { ... } }` stays exactly as the platform
documents it and only the *binding* changes:

```kotlin
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import flowik.core.*
import flowik.intellij.*

fun counterPanel(): DialogPanel {
    val store = CounterStore()
    return context(Bindings()) {
        panel {
            row { label("").text { "Count: ${store.count.value} (×2 = ${store.doubled.value})" } }
            row { button("Increment") { store.inc() } }
        }
    }
}
```

The `Bindings` context argument collects the subscriptions the panel creates so they can be released
together; the section below covers it, along with live vs. Apply-time binding.

## Core API at a glance

From `flowik-core`:

```kotlin
val name    = observable("Alice")              // ObservableValue<String>
val person  = observable(Person("Bob", 30))    // ObservableEntity<Person> — each property reactive
val items   = observables<TodoItem>()          // ObservableEntityList<TodoItem>
val roles   = observableSet("admin")           // ObservableSet<String>

val greeting = computed { "Hello, ${name.value}" }

autoRun { println(greeting.value) }            // re-runs whenever greeting changes
reaction(supply = { name.value }, effect = { newName -> println("name -> $newName") })
whenThen(check = { items.size > 10 }, effect = { println("Over the limit!") })

action {                                       // batch — derivations fire once at the end
    name.value = "Carol"
    person[Person::age].value = 31
}
```

### Deep state: nested objects and lists

`observable(obj)` decomposes one level: every property becomes its own atom. When a property holds
another object — or a list of them — you decide whether it stays atomic or gets decomposed in turn:

```kotlin
data class Address(val city: String, val zip: String)
data class Member(val name: String, val active: Boolean = true)
data class Team(val name: String, val address: Address, val members: List<Member>, val tags: List<String>)

val team = observable(Team("A-Team", Address("Munich", "80331"), listOf(Member("Alice")), listOf("core")))

team[Team::name]                    // ObservableValue<String>  — scalar atom
team[Team::address]                 // ObservableValue<Address>  — the whole object in one atom
team[Team::tags]                    // ObservableList<String>    — reactive list of plain values

team.nested(Team::address)          // ObservableEntity<Address>    — city and zip are separate atoms
team.nestedList(Team::members)      // ObservableEntityList<Member> — each element is an ObservableEntity
```

`nested` / `nestedList` compose, so a tree of any depth is reachable, and typed paths keep the common
cases on one line:

```kotlin
team.nested(Team::address)[Address::city].value = "Berlin"
team[Team::address, Address::city].value = "Berlin"       // the same atom, via a typed path
// paths take up to three steps: company[Company::team, Team::address, Address::city]

val alice = team.nestedList(Team::members)[0]
alice[Member::active].value = false                       // fine-grained: only active-readers re-run
```

Deep access is explicit rather than automatic because a property's type cannot tell the compiler
whether it holds a value to keep atomic or an object to decompose — `KProperty1<T, Address>` and
`KProperty1<T, String>` look the same to overload resolution. It is the same deep/shallow pair the
top-level factories already offer (`observables` vs `observablesShallow`).

Two rules follow from the containers being created lazily and cached:

- **One access pattern per property.** A field is exposed through exactly one container; asking for
  `team[Team::address]` after `team.nested(Team::address)` fails with an `IllegalStateException`
  rather than handing out two independent copies of the same state.
- **`value` is the original snapshot.** Writes go into the containers, not back into the wrapped
  instance, so read current state through the containers — `team.value.address.city` stays as it was.

Reactions stay as fine-grained as the atom they read, at any depth. `subscribe` works the other way
round: a change anywhere in the tree — including inside a list element — is forwarded up to the root's
subscribers. Element property changes deliberately do *not* invalidate readers of a list's *contents*;
a reaction that must re-run on them reads the element property itself:

```kotlin
val activeNames = team.nestedList(Team::members)
    .filter { it[Member::active].value }      // property-reactive, because the predicate reads the atom
    .map { it[Member::name].value }
```

Nullable object properties (`Address?`) have nothing to decompose, so they are rejected at compile time
— keep them atomic with `get(...)`. A `Map`-typed property stays atomic too: the core has no keyed
reactive container to decompose it into.

### Sets: `observableSet`

The equivalent of MobX's `observable.set` — a reactive, insertion-ordered set. Every read
(`items`, `size`, `isEmpty()`, `in`, iteration) auto-tracks, and only mutations that really change
the contents notify anybody:

```kotlin
val selection = observableSet<String>()

autoRun { println("selected ${selection.size}, alice in: ${"alice" in selection}") }

selection.add("alice")            // fires
selection.add("alice")            // duplicate — nobody is notified
selection.toggle("alice")         // removes it, fires
selection.setAll(listOf("bob"))   // replace wholesale, one notification

val upperNames = selection.map { it.uppercase() }        // Computed<List<String>>
val shortNames = selection.filterToSet { it.length < 4 } // Computed<Set<String>>
```

`addAll` / `removeAll` / `setAll` batch internally, so a bulk change re-runs dependents once. It also
works as a property delegate, handing out a plain `MutableSet` view whose mutations stay reactive:

```kotlin
class Store {
    var tags: MutableSet<String> by observableSet("new")
}
```

Elements are stored as plain values (a shallow container — there is no `ObservableEntity`-wrapping
variant, since hashing would key each element on its initial snapshot). For a keyed reactive
collection use `observables(...)`. Fine-grained `SetChange.Add` / `Remove` / `Clear` events are
available through `onChange { }`, mirroring `ObservableList`.

### Change detection: `ref` and `struct`

`observable(...)` decomposes an arbitrary object into one atom per property, and `ObservableValue`
decides "did it change?" with `equals`. Two wrappers cover the cases where that is not what you want —
the equivalents of MobX's `observable.ref` and `observable.struct`:

```kotlin
class Store {
    // One atom holding the whole value, compared by identity (===).
    // Reassigning an equal-but-distinct instance still notifies.
    var session: Session by observableRef(Session.Anonymous)

    // Compared deeply and structurally — arrays and lists included, so a write
    // with the same content notifies nobody.
    var matrix: Array<IntArray> by observableStruct(emptyArray())

    // Or bring your own policy.
    val temperature = observableWith(20.0) { a, b -> abs(a - b) < 0.5 }
}
```

Both are `MutableObservable`s, so they work with two-way bindings (`TextField(store::session)`) exactly
like a plain observable.

The same idea applies to derivations. A plain `computed` propagates *invalidation*: every upstream write
re-runs dependents, even when the derived result is identical. `computedStruct` / `computedRef` notify
only on an actual change:

```kotlin
val isOverLimit = computedStruct { items.size > 10 }   // fires on false <-> true, not on every add
```

Deciding whether the result changed means evaluating it, so these derivations are eager: they
re-evaluate when an upstream observable changes, or once at the end of the enclosing `action`.

Finally, `untracked { }` reads observables without subscribing to them — useful inside a reaction that
must consult a value it should not re-run for.

### Errors in reactions

As in MobX, an exception thrown inside a reaction is *logged, but not re-thrown*. Writing an observable
never fails because some far-away reaction did, and a failing reaction does not stop the others
scheduled alongside it:

```kotlin
autoRun { label.text = store.title.value.substring(0, 10) }   // throws for short titles — logged, dropped
```

Tracking survives the failure, so the reaction runs again on the next change and recovers on its own.
Pass `onError` to handle the failure yourself instead of logging it — `autoRun`, `reaction` and
`whenThen` all accept it:

```kotlin
autoRun(onError = { status.value = it.message }) { render(store.report.value) }

reaction(
    supply = { store.query.value },
    onError = { log.warn("search failed", it) },
    effect = { runSearch(it) },
)

whenThen(check = { store.ready.value }, onError = { showFatal(it) }) { start() }
```

The handler sees exceptions from both parts of a reaction — the tracked expression and the effect.
A `CancellationException` is the one exception that is always re-thrown, so coroutine cancellation is
never swallowed.

Logging goes through SLF4J, under the `flowik.core` category. `flowik-core` depends on the SLF4J API
only — the application picks the binding (logback, `slf4j-simple`, …); with no binding on the
classpath, SLF4J discards the messages.

## The IntelliJ Platform and the Kotlin UI DSL

`flowik-intellij` is a thin adapter between Flowik observables and the platform's own binding
abstractions. The platform builds panels with the [Kotlin UI DSL][ij:ui-dsl] and binds cells to
`ObservableMutableProperty` (live) or `MutableProperty` (deferred, Apply-time); Flowik supplies both
from an observable, so a plugin can keep a MobX-style store and still write ordinary
`panel { row { textField() } }` code.

Nothing here reimplements the UI DSL. Every function is an extension on `Cell`, `Row` or
`Placeholder`, so platform features — `align`, `comment`, `validationOnApply`, groups, collapsible
rows — keep working unchanged.

### `Bindings`: where the subscriptions live

A UI DSL panel is built, not held: `panel { }` returns a `DialogPanel` and the builder receivers are
gone. There is no place to hang the reactions a binding creates, so the bindings take a `Bindings`
(from `flowik-core`) as a **context parameter** and register into it:

```kotlin
class MyToolWindow(parent: Disposable) {
    private val store = ContactStore()
    private val bindings = Bindings()

    val panel: DialogPanel = context(bindings) {
        panel {
            row("Name:") { textField().bindText(store::name) }
        }
    }

    init {
        Disposer.register(parent) { bindings.dispose() }   // tool window closed -> reactions released
    }
}
```

`bindings.dispose()` disposes every reaction and eager derivation the panel created. Tying it to a
platform `Disposable` — the tool window's, the `Configurable`'s, the `DialogWrapper`'s — is what keeps
a closed panel from being kept alive by the store it read.

For a panel whose lifetime is the whole IDE session, `context(Bindings()) { panel { ... } }` (as in the
demo) is fine: the throwaway `Bindings` still satisfies the context parameter, it just never gets
disposed.

Context parameters are a Kotlin 2.2 preview feature, so both the module and its consumers compile
with:

```kotlin
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+ContextParameters"))
}
```

### The binding surface

Everything below requires a `Bindings` in scope.

| Binding                                        | Direction | Notes                                                      |
|------------------------------------------------|-----------|------------------------------------------------------------|
| `Cell<JTextComponent>.bindText(observable)`    | two-way   | also takes `store::name` (a delegated property)            |
| `Cell<JTextComponent>.bindIntText(observable)` | two-way   | platform's `Int` parsing                                   |
| `Cell<JToggleButton>.bindSelected(observable)` | two-way   | checkbox / radio button; also takes `store::flag`          |
| `Cell<ComboBox<T>>.bindItem(observable)`       | two-way   |                                                            |
| `Cell<JLabel>.bindText(observable)`            | one-way   | read-only sources (`Computed`) are accepted                |
| `Cell<JLabel>.text { }`                        | one-way   | derives from an expression — no intermediate `computed`    |
| `Cell.visibleIf { }` / `enabledIf { }`         | one-way   | `Row` has both too                                         |
| `Cell.bindIn(read, update)`                    | one-way   | any aspect with no property: icon, foreground, tooltip …   |
| `Cell.autoRun { }`                             | one-way   | read and write in one block, like a plain `autoRun`        |
| `Placeholder.bindContent { }`                  | one-way   | rebuilds a sub-panel — the DSL's `ForEach` / `SwitchPanel` |

```kotlin
context(Bindings()) {
    panel {
        row("Name:") { textField().bindText(store::name) }
        row("Email:") {
            textField()
                .bindText(store::email)
                .enabledIf { store.name.isNotBlank() }        // predicate, not a property
        }
        row { checkBox("Subscribe").bindSelected(store::subscribed) }
        row { label("").text(store::greeting) }
        row { button("Reset") { store.reset() } }.visibleIf { store.isValid }
    }
}
```

The `visibleIf` / `enabledIf` predicates are the shape worth noticing: no `ObservableProperty` to
build, no `computed` to declare and dispose. They read the store directly and re-apply only on an
actual `false <-> true` transition — internally `computedStruct`, not `computed`, so an unrelated write
does not trigger a layout pass.

`bindIn` and `autoRun` are the escape hatches for aspects the UI DSL has no `bindXxx` for:

```kotlin
icon(AllIcons.General.Warning).bindIn({ store.errors.size }) { isVisible = it > 0 }
cell(myChart).autoRun { model = store.series.value; repaint() }
```

They differ in threading. `bindIn` splits the two halves — the tracked `read` runs on whichever thread
wrote the store, `update` is applied on the EDT — so it is safe when a background task mutates the
store. `autoRun` runs the whole block on the writing thread (deferring it would leave the tracking
scope and lose the dependencies), so it requires EDT mutation, which `runInAction` guarantees.

`Placeholder.bindContent` rebuilds a whole region when the observables it reads change, giving each
rebuild a fresh child `Disposable` so the discarded sub-panel's bindings are released rather than
accumulating:

```kotlin
lateinit var body: Placeholder
val root = panel {
    row { body = placeholder() }
}
body.bindContent {                    // after the panel is built: `panel` is the top-level
    panel {                           // builder here, so the lambda returns a DialogPanel
        for (item in store.items) {
            row { label("").bindText(item[Item::title]) }
        }
    }
}
```

### Live or on Apply

The two contracts the platform offers are both available, and the choice is about semantics rather
than convenience:

- **`asProperty(bindings)`** → `ObservableMutableProperty`. The store is written on every keystroke and
  click, and store changes reach the component immediately. This is what a tool window or an inspector
  wants — there is no OK button to wait for. All the `bindXxx` extensions above use it.
- **`asMutableProperty()`** → `MutableProperty`. The deferred contract: the component is filled from the
  store by `DialogPanel.reset()`, the store is written by `apply()`, and `isModified()` compares the
  two. This is what a `Configurable` needs for OK/Apply/Cancel to mean anything.

```kotlin
row("Timeout:") { intTextField().bindIntText(settings.timeout.asMutableProperty()) }  // Apply-time
row { checkBox("Live preview").bindSelected(viewState::preview) }                     // immediate
```

Both can be mixed in one panel: bind the persisted settings deferred and the ephemeral view state
(filters, expanded nodes, previews) live. `asProperty` also works one-way, on any `ReadableObservable`
— hand a `Computed` to `Cell.visibleIf`, `Row.enabledIf`, or any other platform API that takes an
`ObservableProperty`.

Writes through either wrapper are wrapped in `action`, so one UI event produces one batch, and reads go
through `untracked`: the platform reads properties from arbitrary places, including from inside a
derivation that is rebuilding a panel, and those reads must not silently become dependencies of it.

## Full examples and source

Real, runnable demos live in the repo:

- [`flowik-swing-demo`](flowik-swing-demo) — a Todo app showing list filtering, computed totals,
  keyboard navigation, and conditional visibility.
- [`flowik-vaadin-demo`](flowik-vaadin-demo) — the same Todo app on Vaadin Flow with property
  delegates and two-way input bindings.
- [`flowik-intellij-demo`](flowik-intellij-demo) — an IDE plugin with a tool window built from the
  Kotlin UI DSL: live two-way bindings, a computed label, and a conditionally visible row

Library sources:

- [`flowik-core`](flowik-core/src/main/kotlin/flowik/core) — reactive primitives.
- [`flowik-swing`](flowik-swing/src/main/kotlin/flowik/swing) — Swing component bindings.
- [`flowik-vaadin`](flowik-vaadin/src/main/kotlin/flowik/vaadin) — Vaadin Flow component bindings.
- [`flowik-intellij`](flowik-intellij/src/main/kotlin/flowik/intellij) — Kotlin UI DSL bindings.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

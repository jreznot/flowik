# Flowik

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![team JetBrains project](https://jb.gg/badges/team.svg)](https://github.com/JetBrains#jetbrains-on-github)

Flowik is a small Kotlin library that brings **MobX-style reactive state** to JVM UI toolkits.
You define plain observable values and computed expressions; the UI subscribes itself and re-renders
automatically when anything it reads changes — no listeners, no manual rebinding.

The library is split into a framework-agnostic core and two UI-binding modules:

| Module           | Purpose                                                                 |
|------------------|-------------------------------------------------------------------------|
| `flowik-core`    | Reactive primitives: observables, computed, autoRun, reactions, actions |
| `flowik-swing`   | Bindings for Swing components (depends on `flowik-core`)                |
| `flowik-vaadin`  | Bindings for Vaadin Flow components (depends on `flowik-core`)          |

Most users only need **one** of `flowik-swing` or `flowik-vaadin` — both pull `flowik-core` in
transitively.

## The MobX concept

Flowik is directly inspired by [MobX](https://mobx.js.org/). The core idea, in one sentence:

> **Anything that can be derived from the application state should be derived. Automatically.**

In practice this means three building blocks:

- **Observable state** — mutable values that *track who reads them*.
  Created with `observable(...)`, `observableMap(...)`, `observables(...)`.
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
    implementation("com.github.jreznot.flowik:flowik-swing:v0.1.0")
    implementation("com.github.jreznot.flowik:flowik-vaadin:v0.1.0")
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
    <artifactId>flowik-swing</artifactId> <!-- or flowik-vaadin -->
    <version>v0.1.0</version>
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

## Core API at a glance

From `flowik-core`:

```kotlin
val name    = observable("Alice")              // ObservableValue<String>
val person  = observable(Person("Bob", 30))    // ObservableMap<Person> — each property reactive
val items   = observables<TodoItem>()          // ObservableMapList<TodoItem>

val greeting = computed { "Hello, ${name.value}" }

autoRun { println(greeting.value) }            // re-runs whenever greeting changes
reaction(supply = { name.value }, effect = { newName -> println("name -> $newName") })
whenThen(check = { items.size > 10 }, effect = { println("Over the limit!") })

action {                                       // batch — derivations fire once at the end
    name.value = "Carol"
    person[Person::age].value = 31
}
```

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

## Full examples and source

Real, runnable demos live in the repo:

- [`flowik-swing-demo`](flowik-swing-demo) — a Todo app showing list filtering, computed totals,
  keyboard navigation, and conditional visibility.
- [`flowik-vaadin-demo`](flowik-vaadin-demo) — the same Todo app on Vaadin Flow with property
  delegates and two-way input bindings.

Library sources:

- [`flowik-core`](flowik-core/src/main/kotlin/flowik/core) — reactive primitives.
- [`flowik-swing`](flowik-swing/src/main/kotlin/flowik/swing) — Swing component bindings.
- [`flowik-vaadin`](flowik-vaadin/src/main/kotlin/flowik/vaadin) — Vaadin Flow component bindings.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

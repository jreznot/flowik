package flowik.core

//
// These extensions shadow Kotlin's Iterable.map / filter / flatMap for the
// more specific ObservableItems<T> receiver, so calling them on an ObservableItems
// always returns a reactive Computed<List<R>> rather than a plain List<R>.
//
// Any observable read inside a transform / predicate lambda is auto-tracked,
// meaning the derived updates when list contents change OR when an observed
// property of an element changes (e.g., item[Foo::bar].value inside a filter).

/**
 * Returns a [Computed] list where each element has been transformed by [transform].
 */
fun <T, R> ObservableItems<T>.map(transform: (T) -> R): Computed<List<R>> =
    computed { items.map(transform) }

/**
 * Returns a [Computed] list containing only the elements that satisfy [predicate].
 */
fun <T> ObservableItems<T>.filter(predicate: (T) -> Boolean): Computed<List<T>> =
    computed { items.filter(predicate) }

/**
 * Returns a [Computed] list where each element is transformed to an [Iterable]
 * and the results are concatenated.
 */
fun <T, R> ObservableItems<T>.flatMap(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { items.flatMap(transform) }

//
// Applying map/filter/flatMap to a Computed<List<T>> returns another
// Computed<List<R>>, so operations can be composed without an intermediate
// ObservableItems:
//
//   val visibleNames: Computed<List<String>> = todos
//       .filter { !it[TodoItem::done].value }
//       .map    { it[TodoItem::text].value  }

/**
 * Returns a [Computed] list where each element of this derived list has been
 * transformed by [transform].
 */
fun <T, R> Computed<List<T>>.map(transform: (T) -> R): Computed<List<R>> =
    computed { value.map(transform) }

/**
 * Returns a [Computed] list containing only the elements of this derived list
 * that satisfy [predicate].
 */
fun <T> Computed<List<T>>.filter(predicate: (T) -> Boolean): Computed<List<T>> =
    computed { value.filter(predicate) }

/**
 * Returns a [Computed] list where each element of this derived list is
 * transformed to an [Iterable] and the results are concatenated.
 */
fun <T, R> Computed<List<T>>.flatMap(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { value.flatMap(transform) }

// Named *Values (not map/filter/flatMap) to avoid overload-resolution ambiguity:
// when both an Observables<T> and ObservableItems<T> extension produce the same
// Computed<List<R>>, Kotlin cannot pick the right overload even with an explicit
// lambda parameter type annotation.
//
// filterValues returns Computed<List<ObservableMap<T>>> so callers can still reach
// reactive properties on the results (item[Foo::bar].value in the UI layer).
//
// These are list-reactive only — the derived re-evaluates when items are added
// or removed, but NOT when individual properties of an existing item change.
// For property-reactive predicates use the ObservableItems overload and read
// item[Prop::name].value inside the lambda.

/** Returns a [Computed] list of [ObservableMap] wrappers whose unboxed value satisfies [predicate]. */
fun <T : Any> Observables<T>.filterValues(predicate: (T) -> Boolean): Computed<List<ObservableMap<T>>> =
    computed { items.filter { predicate(it.value) } }

/** Returns a [Computed] list where each unboxed element has been transformed by [transform]. */
fun <T : Any, R> Observables<T>.mapValues(transform: (T) -> R): Computed<List<R>> =
    computed { items.map { transform(it.value) } }

/** Returns a [Computed] list where each unboxed element is expanded by [transform] and flattened. */
fun <T : Any, R> Observables<T>.flatMapValues(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { items.flatMap { transform(it.value) } }

package reaktor.core

//
// These extensions shadow Kotlin's Iterable.map / filter / flatMap for the
// more specific ObservableItems<T> receiver, so calling them on an ObservableItems
// always returns a reactive Derived<List<R>> rather than a plain List<R>.
//
// Any observable read inside a transform / predicate lambda is auto-tracked,
// meaning the derived updates when list contents change OR when an observed
// property of an element changes (e.g. item[Foo::bar].value inside a filter).

/**
 * Returns a [Derived] list where each element has been transformed by [transform].
 */
fun <T, R> ObservableItems<T>.map(transform: (T) -> R): Derived<List<R>> =
    derived { items.map(transform) }

/**
 * Returns a [Derived] list containing only the elements that satisfy [predicate].
 */
fun <T> ObservableItems<T>.filter(predicate: (T) -> Boolean): Derived<List<T>> =
    derived { items.filter(predicate) }

/**
 * Returns a [Derived] list where each element is transformed to an [Iterable]
 * and the results are concatenated.
 */
fun <T, R> ObservableItems<T>.flatMap(transform: (T) -> Iterable<R>): Derived<List<R>> =
    derived { items.flatMap(transform) }

//
// Applying map/filter/flatMap to a Derived<List<T>> returns another
// Derived<List<R>>, so operations can be composed without an intermediate
// ObservableItems:
//
//   val visibleNames: Derived<List<String>> = todos
//       .filter { !it[TodoItem::done].value }
//       .map    { it[TodoItem::text].value  }

/**
 * Returns a [Derived] list where each element of this derived list has been
 * transformed by [transform].
 */
fun <T, R> Derived<List<T>>.map(transform: (T) -> R): Derived<List<R>> =
    derived { value.map(transform) }

/**
 * Returns a [Derived] list containing only the elements of this derived list
 * that satisfy [predicate].
 */
fun <T> Derived<List<T>>.filter(predicate: (T) -> Boolean): Derived<List<T>> =
    derived { value.filter(predicate) }

/**
 * Returns a [Derived] list where each element of this derived list is
 * transformed to an [Iterable] and the results are concatenated.
 */
fun <T, R> Derived<List<T>>.flatMap(transform: (T) -> Iterable<R>): Derived<List<R>> =
    derived { value.flatMap(transform) }

// Named *Values (not map/filter/flatMap) to avoid overload-resolution ambiguity:
// when both an Observables<T> and ObservableItems<T> extension produce the same
// Derived<List<R>>, Kotlin cannot pick the right overload even with an explicit
// lambda parameter type annotation.
//
// filterValues returns Derived<List<Observable<T>>> so callers can still reach
// reactive properties on the results (item[Foo::bar].value in the UI layer).
//
// These are list-reactive only — the derived re-evaluates when items are added
// or removed, but NOT when individual properties of an existing item change.
// For property-reactive predicates use the ObservableItems overload and read
// item[Prop::name].value inside the lambda.

/** Returns a [Derived] list of [Observable] wrappers whose unboxed value satisfies [predicate]. */
fun <T : Any> Observables<T>.filterValues(predicate: (T) -> Boolean): Derived<List<Observable<T>>> =
    derived { items.filter { predicate(it.value) } }

/** Returns a [Derived] list where each unboxed element has been transformed by [transform]. */
fun <T : Any, R> Observables<T>.mapValues(transform: (T) -> R): Derived<List<R>> =
    derived { items.map { transform(it.value) } }

/** Returns a [Derived] list where each unboxed element is expanded by [transform] and flattened. */
fun <T : Any, R> Observables<T>.flatMapValues(transform: (T) -> Iterable<R>): Derived<List<R>> =
    derived { items.flatMap { transform(it.value) } }

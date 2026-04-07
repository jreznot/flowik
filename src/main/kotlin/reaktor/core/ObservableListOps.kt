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

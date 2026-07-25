package flowik.core

//
// These extensions shadow Kotlin's Iterable.map / filter / flatMap for the
// more specific ObservableList<T> receiver, so calling them on an ObservableList
// always returns a reactive Computed<List<R>> rather than a plain List<R>.
//
// Any observable read inside a transform / predicate lambda is auto-tracked,
// meaning the derived updates when list contents change OR when an observed
// property of an element changes (e.g., item[Foo::bar].value inside a filter).

/**
 * Returns a [Computed] list where each element has been transformed by [transform].
 */
fun <T, R> ObservableList<T>.map(transform: (T) -> R): Computed<List<R>> =
    computed { items.map(transform) }

/**
 * Returns a [Computed] list containing only the elements that satisfy [predicate].
 */
fun <T> ObservableList<T>.filter(predicate: (T) -> Boolean): Computed<List<T>> =
    computed { items.filter(predicate) }

/**
 * Returns a [Computed] list where each element is transformed to an [Iterable]
 * and the results are concatenated.
 */
fun <T, R> ObservableList<T>.flatMap(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { items.flatMap(transform) }

//
// Applying map/filter/flatMap to any derived list — from computed { } or from
// computedStruct { } — returns another Computed<List<R>>, so operations can be
// composed without an intermediate ObservableList:
//
//   val visibleNames: Computed<List<String>> = todos
//       .filter { !it[TodoItem::done].value }
//       .map    { it[TodoItem::text].value  }

/**
 * Returns a [Computed] list where each element of this derived list has been
 * transformed by [transform].
 */
fun <T, R> ReadableObservable<List<T>>.map(transform: (T) -> R): Computed<List<R>> =
    computed { value.map(transform) }

/**
 * Returns a [Computed] list containing only the elements of this derived list
 * that satisfy [predicate].
 */
fun <T> ReadableObservable<List<T>>.filter(predicate: (T) -> Boolean): Computed<List<T>> =
    computed { value.filter(predicate) }

/**
 * Returns a [Computed] list where each element of this derived list is
 * transformed to an [Iterable] and the results are concatenated.
 */
fun <T, R> ReadableObservable<List<T>>.flatMap(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { value.flatMap(transform) }
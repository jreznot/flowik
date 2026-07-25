package flowik.core

//
// These extensions shadow Kotlin's Iterable.map / filter / flatMap for the
// more specific ObservableSet<T> receiver, so calling them on an ObservableSet
// always returns a reactive Computed rather than a plain List.
//
// They mirror the ObservableList operations, including the result shape: map
// and flatMap can produce duplicates, so — exactly like the stdlib — they yield
// a List. Use toSet / filterToSet when set semantics are what you need. Every
// result is a Computed<List<R>>, so the ReadableObservable<List<T>> operations
// in ObservableListOps compose on top of them.

/** Returns a [Computed] list where each element has been transformed by [transform]. */
fun <T, R> ObservableSet<T>.map(transform: (T) -> R): Computed<List<R>> =
    computed { items.map(transform) }

/** Returns a [Computed] list containing only the elements that satisfy [predicate]. */
fun <T> ObservableSet<T>.filter(predicate: (T) -> Boolean): Computed<List<T>> =
    computed { items.filter(predicate) }

/**
 * Returns a [Computed] list where each element is transformed to an [Iterable]
 * and the results are concatenated.
 */
fun <T, R> ObservableSet<T>.flatMap(transform: (T) -> Iterable<R>): Computed<List<R>> =
    computed { items.flatMap(transform) }

/** Returns a [Computed] set containing only the elements that satisfy [predicate]. */
fun <T> ObservableSet<T>.filterToSet(predicate: (T) -> Boolean): Computed<Set<T>> =
    computed { items.filterTo(LinkedHashSet(), predicate) }

/** Returns a [Computed] set of the transformed elements, collapsing duplicates. */
fun <T, R> ObservableSet<T>.mapToSet(transform: (T) -> R): Computed<Set<R>> =
    computed { items.mapTo(LinkedHashSet(), transform) }

/**
 * Returns a [Computed] snapshot of the elements — the set-shaped counterpart of
 * reading [ObservableSet.items] inside a derivation.
 */
fun <T> ObservableSet<T>.toSet(): Computed<Set<T>> = computed { items }

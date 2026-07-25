package flowik.core

/**
 * Change-detection policy — the equivalent of MobX's `comparer.*`.
 *
 * A [Comparer] answers one question: given the previous and the new value,
 * did anything change? When it reports the values as equal, no dependent is
 * notified.
 *
 * Used by the atoms and derivations created with [observableRef],
 * [observableStruct], [computedStruct], [computedRef].
 */
fun interface Comparer<in T> {

    /** Returns `true` when [a] and [b] are *the same value*, i.e. no change happened. */
    fun equal(a: T, b: T): Boolean

    companion object {

        /**
         * Reference identity (`===`) — MobX's `observable.ref` semantics.
         *
         * Reassigning an equal-but-distinct instance *does* notify, which is
         * what you want for a value you replace wholesale (a reloaded DTO, a
         * copied domain object). Intended for object references: on boxed
         * primitives identity is unreliable, so use plain [observable] there.
         */
        val Identity: Comparer<Any?> = Comparer { a, b -> a === b }

        /** `equals`-based — the behaviour of [ObservableValue]. */
        val Default: Comparer<Any?> = Comparer { a, b -> a == b }

        /** Deep structural comparison — MobX's `comparer.structural`. See [deepEquals]. */
        val Structural: Comparer<Any?> = Comparer { a, b -> deepEquals(a, b) }
    }
}

/**
 * Structural equality that looks *through* the containers whose own `equals`
 * is not structural:
 *
 * - arrays — object and primitive, nested arrays included;
 * - [List] — element-wise, recursively;
 * - [Map] — same keys, values compared recursively.
 *
 * Everything else (including [Set]) falls back to `equals`. Note that this
 * does **not** reflect into object properties: a data class holding an array
 * is still compared by its generated `equals`, which compares that array by
 * identity. Pass a custom [Comparer] for those.
 */
fun deepEquals(a: Any?, b: Any?): Boolean = when {
    a === b -> true
    a == null || b == null -> false

    a is Array<*> && b is Array<*> -> a.contentDeepEquals(b)
    a is ByteArray && b is ByteArray -> a.contentEquals(b)
    a is ShortArray && b is ShortArray -> a.contentEquals(b)
    a is IntArray && b is IntArray -> a.contentEquals(b)
    a is LongArray && b is LongArray -> a.contentEquals(b)
    a is FloatArray && b is FloatArray -> a.contentEquals(b)
    a is DoubleArray && b is DoubleArray -> a.contentEquals(b)
    a is CharArray && b is CharArray -> a.contentEquals(b)
    a is BooleanArray && b is BooleanArray -> a.contentEquals(b)

    a is List<*> && b is List<*> ->
        a.size == b.size && a.indices.all { deepEquals(a[it], b[it]) }

    a is Map<*, *> && b is Map<*, *> ->
        a.size == b.size && a.all { (key, value) -> b.containsKey(key) && deepEquals(value, b[key]) }

    else -> a == b
}

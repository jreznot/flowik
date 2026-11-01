package flowik.core

/**
 * A computed per argument — mobx-utils' `computedFn`.
 *
 * Called inside a reaction or a computed, `queueOf(deck)` evaluates [compute]
 * for that key through a [Computed] of its own, and caches it: other readers of
 * the same key share the cached value, and a change to what one key read
 * recomputes that key only. It is the answer to "a computed per entity" when
 * the entity cannot carry one — a data class, an id:
 *
 * ```kotlin
 * class Store {
 *     val queueOf = computedFn { deck: ObservableEntity<Deck> -> buildQueue(deck) }
 * }
 * Span().apply { text { "${store.queueOf(deck).size} due" } }
 * ```
 *
 * ### Lifetime
 *
 * An entry lives while something observes it. Once its last reader is gone —
 * at the end of the batch or flush, see [ObservationHooks] — it is dropped, and
 * with it whatever its computation read, so a `computedFn` over a long-lived
 * store does not accumulate entries for keys nobody shows anymore.
 *
 * Called outside any reaction, an unknown key is simply evaluated and nothing
 * is cached, as an unobserved [Computed] would: there would be nothing to keep
 * the entry up to date, or to drop it. A key that is cached because something
 * else observes it is served from the cache.
 *
 * With `keepAlive = true`, entries are created on any call and kept — cached
 * and connected to their sources — until [dispose]. That trades memory for
 * never recomputing; keep the key space bounded.
 *
 * ### Keys
 *
 * Keys are compared with `equals` / `hashCode`, so they should be stable values:
 * an id, a data class, an [ObservableEntity] (equal by the value it was created
 * with). For several arguments, use the two-argument overload or a data class.
 *
 * @param keepAlive keep every entry until [dispose] instead of only while observed
 * @param equals    when given, an entry notifies its readers only when its result
 *                  changes by this comparer — the per-key [computedStruct] /
 *                  [computedRef]. Such entries re-evaluate eagerly, as those do.
 */
fun <K, R> computedFn(
    keepAlive: Boolean = false,
    equals: Comparer<R>? = null,
    compute: (K) -> R
): ComputedFn<K, R> = ComputedFn(keepAlive, equals, compute)

/**
 * The two-argument form of [computedFn]: one cached computation per pair of
 * arguments.
 */
fun <A, B, R> computedFn(
    keepAlive: Boolean = false,
    equals: Comparer<R>? = null,
    compute: (A, B) -> R
): ComputedFn2<A, B, R> = ComputedFn2(ComputedFn(keepAlive, equals) { (a, b): Pair<A, B> -> compute(a, b) })

/** See [computedFn]. Call it like a function; [dispose] drops every cached entry. */
class ComputedFn<K, R> internal constructor(
    private val keepAlive: Boolean,
    private val equals: Comparer<R>?,
    private val compute: (K) -> R
) : (K) -> R, Disposable {

    private class Entry<R>(val value: ReadableObservable<R>, val release: Disposable)

    private val entries = HashMap<K, Entry<R>>()

    override fun invoke(key: K): R {
        entries[key]?.let { return it.value.value }
        // Nothing would keep a new entry current, nor drop it: evaluate, keep nothing.
        if (!keepAlive && Tracking.current == null) return compute(key)

        val entry = createEntry(key)
        entries[key] = entry
        return entry.value.value
    }

    /** Drops every cached entry, releasing what they read. Later calls start afresh. */
    override fun dispose() {
        val released = entries.values.toList()
        entries.clear()
        released.forEach { it.release.dispose() }
    }

    /** The keys currently cached — for tests of the entry lifecycle. */
    internal val cachedKeys: Set<K> get() = entries.keys.toSet()

    private fun createEntry(key: K): Entry<R> {
        val policy = equals?.let { PolicyComputed({ compute(key) }, it) }
        val value: ReadableObservable<R> = policy ?: Computed { compute(key) }

        val release: Disposable = when {
            // A policy computed stays connected until disposed; a plain one
            // stays so while it has a subscriber.
            keepAlive -> policy ?: value.subscribe {}
            else -> {
                val hook = onBecomeUnobserved(value) { evict(key, value) }
                disposables(hook, policy)
            }
        }
        return Entry(value, release)
    }

    private fun evict(key: K, value: ReadableObservable<R>) {
        val entry = entries[key]?.takeIf { it.value === value } ?: return
        entries.remove(key)
        entry.release.dispose()
    }

    private fun disposables(vararg items: Disposable?): Disposable = object : Disposable {
        override fun dispose() {
            items.forEach { it?.dispose() }
        }
    }
}

/** See [computedFn]: the two-argument form. */
class ComputedFn2<A, B, R> internal constructor(
    private val byPair: ComputedFn<Pair<A, B>, R>
) : (A, B) -> R, Disposable {

    override fun invoke(a: A, b: B): R = byPair(a to b)

    override fun dispose() = byPair.dispose()
}

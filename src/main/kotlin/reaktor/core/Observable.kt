package reaktor.core

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Wraps an instance of [T] and exposes each property as an independent
 * reactive container:
 * - scalar properties → [ObservableValue]
 * - [List] properties → [ObservableItems]
 *
 * Containers are created lazily on first access and cached.  Initial values
 * are read from [initial].
 *
 * Example:
 * ```kotlin
 * data class Team(val name: String, val members: List<String>)
 *
 * val team = Observable(Team("A-Team", listOf("Alice", "Bob")))
 *
 * // Type-safe — compiler picks the right overload automatically
 * val name:    ObservableValue<String>  = team[Team::name]
 * val members: ObservableItems<String>   = team[Team::members]
 *
 * // String-based (unchecked cast)
 * val name2:    ObservableValue<String> = team.get("name")
 * val members2: ObservableItems<String>  = team.list("members")
 * ```
 */
class Observable<T : Any>(private val initial: T) {

    /**
     * The wrapped value as originally passed to [Observable].
     *
     * Note: this reflects the *initial* state of [T], not any property
     * mutations made via [get].  Use it for list-level map / filter / flatMap
     * where property-level reactivity is not required.
     */
    val value: T get() = initial

    // Holds ObservableValue<Any?> for scalars, ObservableItems<Any?> for lists.
    private val store = mutableMapOf<String, Any>()

    /**
     * Returns the [ObservableValue] for a scalar property.
     *
     * Kotlin's overload resolution prefers [get(KProperty1<T, List<P>>)] when
     * the property type is [List], so this overload is never called for lists.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <P> get(prop: KProperty1<T, P>): ObservableValue<P> =
        store.getOrPut(prop.name) {
            ObservableValue(prop.get(initial), name = prop.name)
        } as ObservableValue<P>

    /**
     * Returns the [ObservableItems] for a list property.
     *
     * This overload is more specific than [get(KProperty1<T, P>)] and is
     * selected automatically by the compiler when [prop] is typed as
     * `KProperty1<T, List<P>>`.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <P> get(prop: KProperty1<T, List<P>>): ObservableItems<P> =
        store.getOrPut(prop.name) {
            ObservableItems(prop.get(initial))
        } as ObservableItems<P>

    /**
     * Returns the [ObservableValue] for the scalar property named [name].
     *
     * @throws NoSuchElementException if [name] does not match any property on [T].
     * @throws IllegalStateException  if the property is a [List]; use [list] instead.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> get(name: String): ObservableValue<P> {
        val existing = store[name]
        check(existing !is ObservableItems<*>) {
            "Property '$name' is a List — use list(\"$name\") to get its ObservableItems"
        }
        return store.getOrPut(name) {
            val prop = initial::class.memberProperties.find { it.name == name }
                ?: throw NoSuchElementException("No property '$name' on ${initial::class.simpleName}")
            ObservableValue(prop.getter.call(initial), name = name)
        } as ObservableValue<P>
    }

    /**
     * Returns the [ObservableItems] for the list property named [name].
     *
     * @throws NoSuchElementException  if [name] does not match any property on [T].
     * @throws IllegalArgumentException if the property value is not a [List].
     * @throws IllegalStateException    if the property was already accessed as a
     *                                  scalar via [get]; access pattern must be consistent.
     */
    // ── Identity ────────────────────────────────────────────────────────────

    /**
     * Two [Observable] instances are equal when their wrapped [initial]
     * values are equal. This allows [Observables.remove] to locate a
     * wrapper by its original data value.
     */
    override fun equals(other: Any?): Boolean =
        other is Observable<*> && initial == other.initial

    override fun hashCode(): Int = initial.hashCode()

    override fun toString(): String = "Observable($initial)"

    @Suppress("UNCHECKED_CAST")
    fun <P> list(name: String): ObservableItems<P> {
        val existing = store[name]
        check(existing !is ObservableValue<*>) {
            "Property '$name' was already accessed as a scalar ObservableValue — access pattern must be consistent"
        }
        return store.getOrPut(name) {
            val prop = initial::class.memberProperties.find { it.name == name }
                ?: throw NoSuchElementException("No property '$name' on ${initial::class.simpleName}")
            val value = prop.getter.call(initial)
            require(value is List<*>) {
                "Property '$name' is not a List (found ${value?.javaClass?.simpleName})"
            }
            ObservableItems(value as List<P>)
        } as ObservableItems<P>
    }
}

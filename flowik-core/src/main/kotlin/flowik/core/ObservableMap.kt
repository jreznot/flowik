package flowik.core

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Wraps an instance of [T] and exposes each property as an independent
 * reactive container:
 * - Scalar properties → [ObservableValue]
 * - [List] properties → [ObservableList]
 *
 * Containers are created lazily on first access and cached. Initial values
 * are read from [initial].
 *
 * Example:
 * ```kotlin
 * data class Team(val name: String, val members: List<String>)
 *
 * val team = ObservableMap(Team("A-Team", listOf("Alice", "Bob")))
 *
 * // Type-safe — compiler picks the right overload automatically
 * val name:    ObservableValue<String>  = team[Team::name]
 * val members: ObservableList<String>   = team[Team::members]
 *
 * // String-based (unchecked cast)
 * val name2:    ObservableValue<String> = team.get("name")
 * val members2: ObservableList<String>  = team.list("members")
 * ```
 */
class ObservableMap<T : Any>(private val initial: T) : Observable {

    /**
     * The wrapped value as originally passed to [ObservableMap].
     *
     * Note: this reflects the *initial* state of [T], not any property
     * mutations made via [get]. Use it for a list-level map / filter / flatMap
     * where property-level reactivity is not required.
     *
     * Must be mostly used for immutable data structures when you do not mutate properties of items.
     */
    val value: T get() = initial

    // Holds ObservableValue<Any?> for scalars, ObservableList<Any?> for lists.
    private val store = mutableMapOf<String, Any>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    /** Disposables for internal subscriptions to child observables. */
    private val childSubscriptions = mutableMapOf<String, Disposable>()

    /**
     * Returns the [ObservableValue] for a scalar property.
     *
     * Kotlin's overload resolution prefers [get(KProperty1<T, List<P>>)] when
     * the property type is [List], so this overload is never called for lists.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <P> get(prop: KProperty1<T, P>): ObservableValue<P> {
        val obs = store.getOrPut(prop.name) {
            ObservableValue(prop.get(initial), name = prop.name)
        } as ObservableValue<P>
        subscribeToChild(prop.name, obs)
        return obs
    }

    /**
     * Returns the [ObservableList] for a list property.
     *
     * This overload is more specific than [get(KProperty1<T, P>)] and is
     * selected automatically by the compiler when [prop] is typed as
     * `KProperty1<T, List<P>>`.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <P> get(prop: KProperty1<T, List<P>>): ObservableList<P> {
        val obs = store.getOrPut(prop.name) {
            ObservableList(prop.get(initial))
        } as ObservableList<P>
        subscribeToChild(prop.name, obs)
        return obs
    }

    /**
     * Returns the [ObservableValue] for the scalar property named [name].
     *
     * @throws NoSuchElementException if [name] does not match any property on [T].
     * @throws IllegalStateException  if the property is a [List]; use [list] instead.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> get(name: String): ObservableValue<P> {
        val existing = store[name]
        check(existing !is ObservableList<*>) {
            "Property '$name' is a List — use list(\"$name\") to get its ObservableList"
        }
        val obs = store.getOrPut(name) {
            val prop = initial::class.memberProperties.find { it.name == name }
                ?: throw NoSuchElementException("No property '$name' on ${initial::class.simpleName}")
            ObservableValue(prop.getter.call(initial), name = name)
        } as ObservableValue<P>
        subscribeToChild(name, obs)
        return obs
    }

    /**
     * Two [ObservableMap] instances are equal when their wrapped [initial]
     * values are equal. This allows [ObservableMapList.remove] to locate a
     * wrapper by its original data value.
     */
    override fun equals(other: Any?): Boolean =
        other is ObservableMap<*> && initial == other.initial

    override fun hashCode(): Int = initial.hashCode()

    override fun toString(): String = "ObservableMap($initial)"

    /**
     * Returns the [ObservableList] for the list property named [name].
     *
     * @throws NoSuchElementException  if [name] does not match any property on [T].
     * @throws IllegalArgumentException if the property value is not a [List].
     * @throws IllegalStateException    if the property was already accessed as a
     *                                  scalar via [get]; an access pattern must be consistent.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> list(name: String): ObservableList<P> {
        val existing = store[name]
        check(existing !is ObservableValue<*>) {
            "Property '$name' was already accessed as a scalar ObservableValue — access pattern must be consistent"
        }
        val obs = store.getOrPut(name) {
            val prop = initial::class.memberProperties.find { it.name == name }
                ?: throw NoSuchElementException("No property '$name' on ${initial::class.simpleName}")
            val value = prop.getter.call(initial)
            require(value is List<*>) {
                "Property '$name' is not a List (found ${value?.javaClass?.simpleName})"
            }
            ObservableList(value as List<P>)
        } as ObservableList<P>
        subscribeToChild(name, obs)
        return obs
    }

    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    /** Subscribe to a child observable so changes propagate to this map's subscribers. */
    private fun subscribeToChild(name: String, child: Observable) {
        if (name !in childSubscriptions) {
            childSubscriptions[name] = child.subscribe { notifySubscribers() }
        }
    }

    private fun notifySubscribers() {
        subscribers.toList().forEach { it.onChange() }
    }
}

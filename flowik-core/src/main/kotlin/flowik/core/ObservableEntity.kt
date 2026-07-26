package flowik.core

import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Wraps an instance of [T] and exposes each property as an independent
 * reactive container.
 *
 * ### Shallow access — one atom per property
 *
 * - scalar properties → [ObservableValue], via `team[Team::name]` or [get]
 * - [List] properties → [ObservableList] of plain elements, via `team[Team::tags]` or [list]
 *
 * ### Deep access — the nested value is decomposed in turn
 *
 * - object properties → [ObservableEntity], via [nested]
 * - lists of objects → [ObservableEntityList], via [nestedList], so each element's
 *   properties are reactive as well
 *
 * Deep access is *explicit* rather than automatic because a property's Kotlin
 * type cannot tell the compiler whether it holds a value to keep atomic or an
 * object to decompose — `KProperty1<T, Address>` and `KProperty1<T, String>`
 * are the same shape to overload resolution. It mirrors the choice already made
 * at the top level between [observables] (deep) and [observablesShallow]
 * (shallow).
 *
 * Containers are created lazily on first access and cached, so repeated access
 * returns the same instance. Initial values are read from [initial].
 *
 * Example:
 * ```kotlin
 * data class Address(val city: String, val zip: String)
 * data class Member(val name: String, val active: Boolean = true)
 * data class Team(val name: String, val address: Address, val members: List<Member>)
 *
 * val team = observable(Team("A-Team", Address("Munich", "80331"), listOf(Member("Alice"))))
 *
 * // Type-safe — the compiler picks the right overload automatically
 * val name:    ObservableValue<String> = team[Team::name]
 * val address: ObservableMap<Address>  = team.nested(Team::address)
 * val members: ObservableMapList<Member> = team.nestedList(Team::members)
 *
 * address[Address::city].value = "Berlin"        // only city-readers re-run
 * team[Team::address, Address::city].value = "Berlin"   // the same atom, via a typed path
 * members[0][Member::active].value = false      // element property, still fine-grained
 *
 * // String-based (unchecked casts)
 * val zip: ObservableValue<String> = team.nested<Address>("address").get("zip")
 * val tags: ObservableList<String> = team.list("tags")
 * ```
 *
 * ### Propagation
 *
 * A change anywhere in the tree reaches the [subscribe] listeners of every
 * container above it: an atom notifies its owning [ObservableEntity], which
 * notifies the [ObservableEntityList] or [ObservableEntity] that owns *it*, up to the
 * root. Auto-tracking stays fine-grained regardless — a reaction re-runs only
 * for the atoms it actually read.
 *
 * ### One access pattern per property
 *
 * A property is exposed through exactly one container. Reaching for the same
 * property both as an atom and as a nested map would hand out two independent
 * pieces of state for one field, so the second access fails with an
 * [IllegalStateException] naming the accessor already in use.
 *
 * ### Snapshots
 *
 * [snapshot] is the instance originally passed in; writes through the containers do
 * not write back into it (the same holds for shallow property mutation, and is
 * why the wrapped type is normally immutable). Read current state through the
 * containers, not through [snapshot].
 */
class ObservableEntity<T : Any>(private val initial: T) : Observable {

    /**
     * The wrapped value as originally passed to [ObservableEntity].
     *
     * Note: this reflects the *initial* state of [T], not any property
     * mutations made via [get] or [nested]. Use it for a list-level map /
     * filter / flatMap where property-level reactivity is not required.
     *
     * Must be mostly used for immutable data structures when you do not mutate properties of items.
     */
    val snapshot: T get() = initial

    /** One container per accessed property: an atom, a list, a nested map, or a deep list. */
    private val store = mutableMapOf<String, Observable>()

    /** External subscribers registered via [subscribe]. */
    private val subscribers = mutableListOf<Observer>()

    /** Disposables for internal subscriptions to child observables. */
    private val childSubscriptions = mutableMapOf<String, Disposable>()

    // Shallow access — the property value stays in one container.

    /**
     * Returns the [ObservableValue] for a scalar property.
     *
     * Kotlin's overload resolution prefers [get(KProperty1<T, List<P>>)] when
     * the property type is [List], so this overload is never called for lists.
     * An object-typed property is kept atomic here; use [nested] to decompose it.
     */
    operator fun <P> get(prop: KProperty1<T, P>): ObservableValue<P> =
        container(prop.name, Access.VALUE) { ObservableValue(prop.get(initial), name = prop.name) }

    /**
     * Returns the [ObservableList] for a list property, holding the elements as
     * plain values.
     *
     * This overload is more specific than [get(KProperty1<T, P>)] and is
     * selected automatically by the compiler when [prop] is typed as
     * `KProperty1<T, List<P>>`. Use [nestedList] when the elements are objects
     * whose properties should be reactive too.
     */
    operator fun <P> get(prop: KProperty1<T, List<P>>): ObservableList<P> =
        container(prop.name, Access.LIST) { ObservableList(prop.get(initial)) }

    /**
     * Returns the [ObservableValue] for the scalar property named [name].
     *
     * @throws NoSuchElementException if [name] does not match any property on [T].
     * @throws IllegalStateException  if the property is already exposed as something
     *                                else; the message names the accessor to use.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> get(name: String): ObservableValue<P> =
        container(name, Access.VALUE) { ObservableValue(propertyValue(name) as P, name = name) }

    /**
     * Returns the [ObservableList] for the list property named [name], holding
     * the elements as plain values.
     *
     * @throws NoSuchElementException   if [name] does not match any property on [T].
     * @throws IllegalArgumentException if the property value is not a [List].
     * @throws IllegalStateException    if the property is already exposed as something
     *                                  else; an access pattern must be consistent.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P> list(name: String): ObservableList<P> =
        container(name, Access.LIST) { ObservableList(requireList(name, propertyValue(name)) as List<P>) }

    // Deep access — the property value is decomposed into containers of its own.

    /**
     * Returns the [ObservableEntity] decomposing the object held by [prop], so its
     * own properties are individually reactive — and can be decomposed further.
     *
     * The bound `P : Any` keeps nullable properties out: there is nothing to
     * decompose when the value is absent, so expose those with [get] instead.
     *
     * @throws IllegalArgumentException if the value is not a decomposable object
     *         (a number, string, enum, collection, …).
     * @throws IllegalStateException    if the property is already exposed as something else.
     */
    fun <P : Any> nested(prop: KProperty1<T, P>): ObservableEntity<P> =
        container(prop.name, Access.NESTED) { ObservableEntity(requireObject(prop.name, prop.get(initial))) }

    /**
     * Returns the [ObservableEntity] decomposing the object held by the property
     * named [name].
     *
     * @throws NoSuchElementException   if [name] does not match any property on [T].
     * @throws IllegalArgumentException if the value is `null` or is not a decomposable object.
     * @throws IllegalStateException    if the property is already exposed as something else.
     */
    fun <P : Any> nested(name: String): ObservableEntity<P> =
        container(name, Access.NESTED) { ObservableEntity(requireObject<P>(name, propertyValue(name))) }

    /**
     * Returns the [ObservableEntityList] for a list-of-objects property: the list
     * itself is reactive, and each element is wrapped in an [ObservableEntity], so
     * `nestedList(Team::members)[0][Member::name]` is a fine-grained atom.
     *
     * Element property changes reach this map's [subscribe] listeners, but — as
     * with any [ObservableEntityList] — they do not invalidate readers of the list
     * *contents*.
     *
     * @throws IllegalArgumentException if the value is not a [List], or holds nulls
     *         or plain values rather than objects.
     * @throws IllegalStateException    if the property is already exposed as something else.
     */
    fun <P : Any> nestedList(prop: KProperty1<T, List<P>>): ObservableEntityList<P> =
        container(prop.name, Access.NESTED_LIST) {
            requireObjectElementType(prop.name, prop)
            ObservableEntityList(requireObjectList(prop.name, prop.get(initial)))
        }

    /**
     * Returns the [ObservableEntityList] for the list-of-objects property named [name].
     *
     * @throws NoSuchElementException   if [name] does not match any property on [T].
     * @throws IllegalArgumentException if the value is not a [List], or holds nulls
     *         or plain values rather than objects.
     * @throws IllegalStateException    if the property is already exposed as something else.
     */
    fun <P : Any> nestedList(name: String): ObservableEntityList<P> =
        container(name, Access.NESTED_LIST) {
            val prop = property(name)
            requireObjectElementType(name, prop)
            ObservableEntityList(requireObjectList<P>(name, prop.getter.call(initial)))
        }

    // Typed paths — sugar for walking two or three levels down in one expression.
    // Each step is a nested() call, so the containers are the same ones a manual
    // walk would hand out. Chain nested() directly to go deeper.

    /** The atom at `prop.sub`, e.g. `team[Team::address, Address::city]`. */
    operator fun <P : Any, R> get(prop: KProperty1<T, P>, sub: KProperty1<P, R>): ObservableValue<R> =
        nested(prop)[sub]

    /** The shallow list at `prop.sub`, chosen over the scalar overload when `sub` is a [List]. */
    operator fun <P : Any, R> get(prop: KProperty1<T, P>, sub: KProperty1<P, List<R>>): ObservableList<R> =
        nested(prop)[sub]

    /** The atom at `first.second.third`. */
    operator fun <P : Any, Q : Any, R> get(
        first: KProperty1<T, P>,
        second: KProperty1<P, Q>,
        third: KProperty1<Q, R>
    ): ObservableValue<R> = nested(first).nested(second)[third]

    /** The shallow list at `first.second.third`. */
    operator fun <P : Any, Q : Any, R> get(
        first: KProperty1<T, P>,
        second: KProperty1<P, Q>,
        third: KProperty1<Q, List<R>>
    ): ObservableList<R> = nested(first).nested(second)[third]

    override fun subscribe(observer: Observer): Disposable {
        subscribers.add(observer)
        return object : Disposable {
            override fun dispose() {
                subscribers.remove(observer)
            }
        }
    }

    /**
     * Two [ObservableEntity] instances are equal when their wrapped [initial]
     * values are equal. This allows [ObservableEntityList.remove] to locate a
     * wrapper by its original data value.
     */
    override fun equals(other: Any?): Boolean =
        other is ObservableEntity<*> && initial == other.initial

    override fun hashCode(): Int = initial.hashCode()

    override fun toString(): String = "ObservableMap($initial)"

    /**
     * Returns the container for [name], creating it on first access. A second
     * access with a different [access] kind is a programming error: the property
     * would end up with two independent pieces of state.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <C : Observable> container(name: String, access: Access, create: () -> C): C {
        val existing = store[name]
        if (existing != null) {
            val current = accessOf(existing)
            check(current == access) {
                "Property '$name' is already exposed as ${current.hint(name)} and cannot also be " +
                        "exposed as ${access.hint(name)} — an access pattern must be consistent"
            }
            return existing as C
        }
        val created = create()
        store[name] = created
        subscribeToChild(name, created)
        return created
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

    /**
     * Looks the property named [name] up on [T].
     *
     * The getter is forced accessible so that non-public model types — a private
     * data class inside a store, say — work through the string-based accessors too.
     */
    private fun property(name: String): KProperty<*> {
        val prop = initial::class.memberProperties.find { it.name == name }
            ?: throw NoSuchElementException("No property '$name' on ${initial::class.simpleName}")
        prop.isAccessible = true
        return prop
    }

    /** Reads the property named [name] off [initial] reflectively. */
    private fun propertyValue(name: String): Any? = property(name).getter.call(initial)

    private fun requireList(name: String, value: Any?): List<*> {
        require(value is List<*>) {
            "Property '$name' is not a List (found ${value?.let { it::class.simpleName } ?: "null"})"
        }
        return value
    }

    @Suppress("UNCHECKED_CAST")
    private fun <P : Any> requireObject(name: String, value: Any?): P {
        requireNotNull(value) {
            "Property '$name' is null — there is nothing to decompose; " +
                    "expose a nullable property as an atom with get(\"$name\")"
        }
        require(!isAtomic(value)) {
            "Property '$name' holds a ${value::class.simpleName}, which is a value rather than a " +
                    "decomposable object — use ${atomicAlternative(name, value)}"
        }
        return value as P
    }

    /**
     * Rejects `List<String>` and friends by their *declared* element type, which
     * an empty list cannot reveal — [requireObjectList] only sees the elements
     * that are actually there.
     */
    private fun requireObjectElementType(name: String, prop: KProperty<*>) {
        val element = (prop.returnType.arguments.singleOrNull()?.type?.classifier as? KClass<*>)?.javaObjectType
            ?: return
        require(!isAtomic(element)) {
            "Property '$name' is a List<${element.simpleName}>, and ${element.simpleName} values are not " +
                    "decomposable — use list(\"$name\") for a shallow ObservableList"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <P : Any> requireObjectList(name: String, value: Any?): List<P> {
        val list = requireList(name, value)
        list.forEachIndexed { index, element ->
            requireNotNull(element) {
                "Element $index of property '$name' is null — a deep list cannot wrap nulls; " +
                        "use list(\"$name\") for a shallow ObservableList"
            }
            require(!isAtomic(element)) {
                "Property '$name' holds ${element::class.simpleName} values rather than objects — " +
                        "use list(\"$name\") for a shallow ObservableList"
            }
        }
        return list as List<P>
    }

    private fun atomicAlternative(name: String, value: Any): String = when (value) {
        is Collection<*> -> "list(\"$name\") or nestedList(\"$name\")"
        else -> "get(\"$name\")"
    }

    /** How a property is exposed. One property has exactly one of these. */
    private enum class Access(private val container: String, private val accessor: String) {
        VALUE("a scalar ObservableValue", "get"),
        LIST("a shallow ObservableList", "list"),
        NESTED("a nested ObservableMap", "nested"),
        NESTED_LIST("a deep ObservableMapList", "nestedList");

        fun hint(property: String): String = "$container ($accessor(\"$property\"))"
    }

    private fun accessOf(container: Observable): Access = when (container) {
        is ObservableEntityList<*> -> Access.NESTED_LIST
        is ObservableList<*> -> Access.LIST
        is ObservableEntity<*> -> Access.NESTED
        else -> Access.VALUE
    }
}

/**
 * Types that have no meaningful properties to decompose — decomposing a [String]
 * into `length` is never what the caller meant. Keeps [ObservableEntity]'s deep
 * accessors from silently producing a nonsense wrapper.
 */
@Suppress("RemoveRedundantQualifierName")
private val ATOMIC_TYPES: List<Class<*>> = listOf(
    CharSequence::class.java,
    Number::class.java,
    java.lang.Boolean::class.java,
    Character::class.java,
    Enum::class.java,
    Collection::class.java,
    Map::class.java,
    Temporal::class.java,
    TemporalAmount::class.java,
    java.util.Date::class.java,
    UUID::class.java
)

/** Compared on the boxed type, so a primitive never slips through unmatched. */
private fun isAtomic(type: Class<*>): Boolean =
    type.isArray || type.isPrimitive || ATOMIC_TYPES.any { it.isAssignableFrom(type) }

private fun isAtomic(value: Any): Boolean = isAtomic(value.javaClass)

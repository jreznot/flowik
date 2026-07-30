package flowik.core.viewmodel

import flowik.core.MutableObservable
import flowik.core.Observable
import flowik.core.ObservableList
import flowik.core.ObservableSet
import flowik.core.ReadableObservable
import flowik.core.untracked
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * A [ViewModel] over a store object that holds atoms of its own — what
 * [createViewModel] returns for anything that is not an [flowik.core.ObservableEntity].
 *
 * Both ways of holding an atom are found, and both are edited the same way:
 *
 * ```kotlin
 * class Settings {
 *     var host: String by observable("localhost")   // delegated — the property is the value
 *     val port = observable(8080)                   // the property is the atom
 * }
 *
 * val settings = Settings()
 * val form = createViewModel(settings)
 *
 * form[Settings::host] = "example.org"              // typed by the property reference
 * form.property(settings.port).value = 9090         // typed by the atom itself
 * form.get<Int>("port").value                       // or by name
 *
 * form.submit()                                     // one action — the store fires once
 * ```
 *
 * A property reference only types the value for the *delegated* shape:
 * `Settings::port` is a `KProperty1<Settings, ObservableValue<Int>>`, so
 * `form[Settings::port]` would claim to return the atom rather than the `Int` it
 * really returns. Reach those through [property] with the atom, or by name.
 *
 * ### What is buffered
 *
 * A property qualifies when it holds a writable atom — [flowik.core.ObservableValue], the
 * atoms behind `observableRef` / `observableStruct`, any [flowik.core.MutableObservable].
 * Everything else is rejected on access, with a message saying why:
 *
 * - a `computed { }` is read-only, so there is nothing to submit;
 * - an [flowik.core.ObservableList] / [flowik.core.ObservableSet] is a container rather than a value —
 *   see [ViewModel] on why those are not buffered;
 * - a plain property is not reactive at all. To edit a plain object, wrap it:
 *   `createViewModel(observable(dto))`.
 *
 * The store is scanned once, when the view model is created. Only getters of
 * properties *declared* to return a [flowik.core.MutableObservable] are called — a delegate
 * is read from its field — so scanning never triggers a computation, and it
 * never registers a dependency.
 */
class StoreViewModel<T : Any> internal constructor(override val model: T) : ViewModel<T>() {

    /** The store's writable atoms, by property name. */
    private val atoms = LinkedHashMap<String, MutableObservable<Any?>>()

    /** Why the remaining properties cannot be buffered, by property name. */
    private val rejected = LinkedHashMap<String, String>()

    init {
        scan()
    }

    /** The names of the store properties this view model can buffer. */
    val propertyNames: Set<String> get() = atoms.keys

    /**
     * Returns the buffered atom in front of [source], the store's own atom —
     * `form.property(settings.port)`.
     *
     * The type-safe way to reach a property that holds its atom rather than being
     * delegated to it, where a property reference would type the value as the
     * atom. Equivalent to [get] with that property's name.
     *
     * @throws IllegalArgumentException if [source] is not one of the store's atoms.
     */
    fun <P> property(source: MutableObservable<P>): ViewModelProperty<P> {
        val name = atoms.entries.firstOrNull { (_, atom) -> atom === source }?.key
            ?: throw IllegalArgumentException(
                "$source is not an atom of $modelTypeName — a view model can only buffer the model's own properties"
            )
        return get(name)
    }

    override fun atom(name: String): MutableObservable<Any?> =
        atoms[name] ?: throw rejection(name)

    private val modelTypeName: String get() = model::class.simpleName ?: model::class.toString()

    /**
     * Collects the store's atoms, and a reason for every property that is not one,
     * so the error a caller eventually sees names the actual problem.
     */
    private fun scan() {
        for (property in model::class.memberProperties) {
            @Suppress("UNCHECKED_CAST")
            val prop = property as KProperty1<Any, *>
            val name = prop.name
            when (val holder = holderOf(prop)) {
                is ObservableList<*>, is ObservableSet<*> -> rejected[name] =
                    "Property '$name' of $modelTypeName is ${holder!!::class.simpleName}, a reactive collection " +
                            "rather than a value — a view model cannot buffer one; edit it directly, or keep the " +
                            "edited copy in an atom of its own"

                is MutableObservable<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    atoms[name] = holder as MutableObservable<Any?>
                }

                is ReadableObservable<*> -> rejected[name] =
                    "Property '$name' of $modelTypeName is read-only (${holder::class.simpleName}) — " +
                            "there is nothing to submit into it"

                else -> rejected[name] =
                    "Property '$name' of $modelTypeName does not hold an observable atom — a view model buffers " +
                            "properties written as `var $name by observable(…)` or `val $name = observable(…)`. " +
                            "To edit a plain object, wrap it first: createViewModel(observable(model))"
            }
        }
    }

    /**
     * The atom a property holds, if any: its delegate, or — when it is *declared*
     * to return one — its value.
     *
     * Restricting the getter call to the declared type keeps the scan free of
     * side effects; the delegate is read straight from its field either way.
     */
    private fun holderOf(prop: KProperty1<Any, *>): Any? {
        prop.isAccessible = true
        return untracked {
            runCatching {
                if (declaresObservable(prop)) prop.getter.call(model) else prop.getDelegate(model)
            }.getOrNull()
        }
    }

    private fun declaresObservable(prop: KProperty1<Any, *>): Boolean =
        (prop.returnType.classifier as? KClass<*>)?.isSubclassOf(Observable::class) == true

    private fun rejection(name: String): RuntimeException =
        rejected[name]?.let { IllegalArgumentException(it) }
            ?: NoSuchElementException("No property '$name' on $modelTypeName")
}

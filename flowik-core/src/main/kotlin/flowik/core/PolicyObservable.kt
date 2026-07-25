package flowik.core

import kotlin.reflect.KProperty

/**
 * An observable atom whose change detection is supplied by a [Comparer] —
 * the delegated-wrapper counterpart of MobX's `observable.ref` and
 * `observable.struct` decorators.
 *
 * Internal: reach it through [observableRef], [observableStruct] or
 * [observableWith], all of which hand back a [MutableObservable].
 *
 * Two things distinguish it from [ObservableValue]:
 *
 * - **Change detection is pluggable.** [ObservableValue] always compares with
 *   `equals`; here the [comparer] decides, so identity ([Comparer.Identity])
 *   and deep structural ([Comparer.Structural]) semantics are both available.
 * - **The value stays atomic.** `observable(someObject)` decomposes an
 *   arbitrary object into one atom per property ([ObservableMap]). This wrapper
 *   holds the whole value in a single atom, which is what you want for
 *   immutable snapshots, sealed-class state, arrays, or anything you replace
 *   wholesale.
 *
 * ```kotlin
 * class Store {
 *     var session: Session by observableRef(Session.Anonymous)
 *     var matrix: Array<IntArray> by observableStruct(emptyArray())
 * }
 * ```
 *
 * Implementation note: reads register a dependency on a private version atom
 * rather than on the wrapper itself, because [Tracker.addDependency] is typed
 * to [ObservableValue]. This is the same delegation [ObservableList] uses for
 * its version counter, and it means writes still respect [action] batching.
 */
internal class PolicyObservable<T>(
    initial: T,
    private val name: String? = null,
    private val comparer: Comparer<T>,
) : MutableObservable<T> {

    /** The tracking atom — dependents observe this, never the wrapper. */
    private val version = ObservableValue(0L, name = "${name ?: "policy"}-version")

    private var revision = 0L

    private var current: T = initial

    override var value: T
        get() {
            version.value // touch the version to register dependency
            return current
        }
        set(new) {
            if (!comparer.equal(current, new)) {
                current = new
                version.value = ++revision
            }
        }

    override fun subscribe(observer: Observer): Disposable = version.subscribe(observer)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun get(): T = value

    override fun toString(): String = "PolicyObservable(${name ?: "?"}=$current)"
}

package flowik.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

// Each overload is more specific than the generic fallback below, so Kotlin's
// overload resolution will always route these types here.  The name parameter
// is kept for debugging / toString purposes.

fun observable(initial: Boolean, name: String? = null): ObservableValue<Boolean> = ObservableValue(initial, name)
fun observable(initial: Int, name: String? = null): ObservableValue<Int> = ObservableValue(initial, name)
fun observable(initial: Long, name: String? = null): ObservableValue<Long> = ObservableValue(initial, name)
fun observable(initial: Double, name: String? = null): ObservableValue<Double> = ObservableValue(initial, name)
fun observable(initial: Float, name: String? = null): ObservableValue<Float> = ObservableValue(initial, name)
fun observable(initial: Char, name: String? = null): ObservableValue<Char> = ObservableValue(initial, name)
fun observable(initial: Byte, name: String? = null): ObservableValue<Byte> = ObservableValue(initial, name)
fun observable(initial: Short, name: String? = null): ObservableValue<Short> = ObservableValue(initial, name)
fun observable(initial: String, name: String? = null): ObservableValue<String> = ObservableValue(initial, name)
fun observable(initial: BigDecimal, name: String? = null): ObservableValue<BigDecimal> = ObservableValue(initial, name)
fun observable(initial: LocalDate, name: String? = null): ObservableValue<LocalDate> = ObservableValue(initial, name)
fun observable(initial: LocalTime, name: String? = null): ObservableValue<LocalTime> = ObservableValue(initial, name)
fun observable(initial: LocalDateTime, name: String? = null): ObservableValue<LocalDateTime> =
    ObservableValue(initial, name)

// Any type not matched by the overloads above (data classes, domain objects,
// etc.) is wrapped in an ObservableMap, exposing each property as an
// individual ObservableValue.

/** Wrap an arbitrary [T] instance, exposing each property as an [ObservableValue]. */
fun <T : Any> observable(initial: T): ObservableMap<T> = ObservableMap(initial)

/** Explicit alias — prefer [observable] for brevity. */
fun <T : Any> observableMap(initial: T): ObservableMap<T> = ObservableMap(initial)

/** Create an [Observables] pre-populated with [items]. */
fun <T : Any> observables(vararg items: T): Observables<T> =
    Observables(items.toList())

/** Create a computed (derived) value with auto-tracking. */
fun <T> computed(compute: () -> T): Computed<T> = Computed(compute)

/**
 * Create and immediately run a reaction. Returns the [Reaction] so it can
 * be disposed later (e.g. when a component is removed from the hierarchy).
 */
fun reaction(name: String? = null, effect: () -> Unit): Reaction {
    val r = Reaction(name, effect)
    r.run()
    return r
}

/**
 * Create and immediately run an [AutoRun] — the core equivalent of MobX's
 * `autorun`. The [effect] is executed synchronously on the current thread
 * and re-runs whenever any observed dependency changes.
 *
 * Returns the [AutoRun] so it can be [disposed][AutoRun.dispose] later.
 */
fun autoRun(name: String? = null, effect: () -> Unit): AutoRun {
    val ar = AutoRun(name, effect)
    ar.run()
    return ar
}

/**
 * Create and immediately evaluate a [When] — the core equivalent of MobX's
 * `when`.  The [predicate] is evaluated reactively; as soon as it returns
 * `true`, the [effect] is executed **once** and the reaction auto-disposes.
 *
 * If the predicate is already `true` on the first evaluation, the effect
 * fires immediately.
 *
 * Returns the [When] (a [Disposable]) so callers can cancel the reaction
 * before the predicate ever becomes `true`.
 */
fun whenThen(predicate: () -> Boolean, name: String? = null, effect: () -> Unit): When {
    val w = When(name, predicate, effect)
    w.run()
    return w
}

/**
 * Batch multiple observable writes so that reactions fire only once,
 * after the block completes.
 */
inline fun <R> action(block: () -> R): R {
    Tracking.beginBatch()
    return try {
        block()
    } finally {
        Tracking.endBatch()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> unwrapBinding(prop: KProperty0<T>): ObservableValue<T> {
    prop.isAccessible = true
    val delegate = prop.getDelegate() ?: throw IllegalArgumentException("Property must have a delegate")
    return delegate as ObservableValue<T>
}
package flowik.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

// Each overload is more specific than the generic fallback below, so Kotlin's
// overload resolution will always route these types here. The name parameter
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

// Any type not matched by the overloads above (data classes, domain objects, etc.)
// is wrapped in an ObservableMap, exposing each property as an individual ObservableValue.

/** Wrap an arbitrary [T] instance, exposing each property as an [ObservableValue]. */
fun <T : Any> observable(initial: T): ObservableMap<T> = ObservableMap(initial)

/** Explicit alias — prefer [observable] for brevity. */
fun <T : Any> observableMap(initial: T): ObservableMap<T> = ObservableMap(initial)

/** Create an [ObservableMapList] pre-populated with [items]. */
fun <T : Any> observables(vararg items: T): ObservableMapList<T> =
    ObservableMapList(items.toList())

/** Create a computed (derived) value with auto-tracking. */
fun <T> computed(compute: () -> T): Computed<T> = Computed(compute)

/**
 * Create a reaction with MobX-style semantics. Returns the [Disposable] so it
 * can be disposed later (e.g., when a component is removed from the hierarchy).
 *
 * [supply] is tracked — observables read inside it become dependencies.
 * [effect] runs (receiving the current data value) whenever the tracked data
 * changes. Does NOT fire on creation.
 */
fun <T> reaction(
    name: String? = null,
    supply: () -> T,
    effect: (T) -> Unit
): Disposable {
    val r = Reaction(name, supply, effect)
    r.run()
    return r
}

/**
 * Create and immediately run an [AutoRun] — the core equivalent of MobX's
 * `autorun`. Returns the [Disposable] so it can be disposed later.
 */
fun autoRun(
    name: String? = null,
    effect: () -> Unit
): Disposable {
    val ar = AutoRun(name, effect)
    ar.run()
    return ar
}

/**
 * Create and immediately evaluate a [When] — the core equivalent of MobX's
 * `when`.  The [check] is evaluated reactively; as soon as it returns
 * `true`, the [effect] is executed **once** and the reaction auto-disposes.
 *
 * If the predicate is already `true` on the first evaluation, the effect
 * fires immediately.
 *
 * Returns the [When] (a [Disposable]) so callers can cancel the reaction
 * before the predicate ever becomes `true`.
 */
fun whenThen(
    name: String? = null,
    check: () -> Boolean,
    effect: () -> Unit
): Disposable {
    val w = When(name, check, effect)
    w.run()
    return w
}

/**
 * Batch multiple observable writes so that reactions fire only once, after the block completes.
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

fun not(value: ObservableValue<Boolean>): Computed<Boolean> = computed { !value.value }
fun or(vararg values: ObservableValue<Boolean>): Computed<Boolean> = computed { values.any { it.value } }
fun and(vararg values: ObservableValue<Boolean>): Computed<Boolean> = computed { values.all { it.value } }

fun ObservableValue<Boolean>.toggle() {
    value = !value
}
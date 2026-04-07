package reaktor.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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
// etc.) is wrapped in an Observable, exposing each property as an
// individual ObservableValue.

/** Wrap an arbitrary [T] instance, exposing each property as an [ObservableValue]. */
fun <T : Any> observable(initial: T): Observable<T> = Observable(initial)

/** Explicit alias — prefer [observable] for brevity. */
fun <T : Any> observableObject(initial: T): Observable<T> = Observable(initial)

/** Create an [Observables] pre-populated with [items]. */
fun <T : Any> observables(vararg items: T): Observables<T> =
    Observables(items.toList())

// ── Other reactive primitives ─────────────────────────────────────────────────

/** Create a computed (derived) value with auto-tracking. */
fun <T> derived(compute: () -> T): Derived<T> = Derived(compute)

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

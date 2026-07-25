package flowik.core

import java.util.function.Supplier
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty

/**
 * A readable reactive value.
 *
 * Reading [value] registers a dependency with the enclosing reaction or
 * computed, exactly like [ObservableValue.value] does. Implementations can be
 * used as a `val` property delegate, passed to one-way bindings as a
 * [Supplier], and subscribed to via [Observable.subscribe].
 *
 * Implemented by [Computed], by the derivations [computedStruct] returns, and —
 * through [MutableObservable] — by every writable observable.
 */
interface ReadableObservable<T> : Observable, Supplier<T>, ReadOnlyProperty<Any?, T> {

    /** The current value. Reading this auto-tracks. */
    val value: T
}

/**
 * A readable *and* writable reactive value — the contract two-way UI bindings
 * need, and the reason they are typed to this interface rather than to
 * [ObservableValue] directly.
 *
 * Implemented by [ObservableValue] (equality-based change detection) and by the
 * atoms [observableRef] / [observableStruct] return (change detection supplied
 * by a [Comparer]), so the latter drive the same bindings as plain observables.
 */
interface MutableObservable<T> : ReadableObservable<T>, ReadWriteProperty<Any?, T> {

    /** The current value. Reading auto-tracks; writing notifies dependents. */
    override var value: T
}

/**
 * A readable reactive value that owns a subscription and can be released —
 * what [computedStruct], [computedRef] and [computedWith] return.
 *
 * Disposing stops the derivation from observing its upstream, so it no longer
 * notifies dependents and its value stays frozen at the last observed result.
 */
interface DisposableObservable<T> : ReadableObservable<T>, Disposable

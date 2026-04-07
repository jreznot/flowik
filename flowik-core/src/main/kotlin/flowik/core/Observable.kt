package flowik.core

/**
 * A reactive source that notifies [Observer] subscribers when its value changes.
 *
 * Implemented by [ObservableValue], [Computed], [ObservableItems],
 * [ObservableMap], and [Observables].
 */
interface Observable {

    /**
     * Registers [observer] to be notified when this observable changes.
     *
     * @return a [Disposable] that, when disposed, removes the subscription.
     */
    fun subscribe(observer: Observer): Disposable
}

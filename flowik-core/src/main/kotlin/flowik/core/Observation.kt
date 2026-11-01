package flowik.core

/**
 * An observable that can say when it starts and stops being *observed* — MobX's
 * `onBecomeObserved` / `onBecomeUnobserved`.
 *
 * Observed means read by at least one reaction or computed: the first such
 * reader arriving fires [onBecomeObserved], the last one leaving fires
 * [onBecomeUnobserved]. That is the moment to start and stop whatever produces
 * the value — a timer, a subscription, a cache entry — so it runs only while
 * someone is looking:
 *
 * ```kotlin
 * val now = observable(Instant.now())
 * var timer: Timer? = null
 * now.onBecomeObserved { timer = startTicking { now.value = Instant.now() } }
 * now.onBecomeUnobserved { timer?.cancel() }
 * ```
 *
 * Losing the last reader only counts once the batch, the flush of reactions or
 * the reaction run in progress is over, so a value that is dropped and read
 * again within one pass — a reaction re-running, a screen rebuilt in the same
 * flush — fires neither hook.
 *
 * Listeners run untracked, so what they read does not become a dependency of
 * whatever reaction happened to trigger them. An exception thrown by one is
 * logged and does not stop the others. A listener should not write observables
 * synchronously — it runs in the middle of a reaction — but may start work that
 * writes them later.
 *
 * Implemented by [ObservableValue] and [Computed], and by the collections and
 * wrappers built on them. Plain [subscribe][Observable.subscribe] listeners do
 * not make an atom observed; a [Computed] counts them, because it has to stay
 * connected to its sources to notify them.
 */
interface ObservationHooks {

    /** Runs [listener] each time this goes from no readers to one. */
    fun onBecomeObserved(listener: () -> Unit): Disposable

    /** Runs [listener] each time this goes from some readers to none. */
    fun onBecomeUnobserved(listener: () -> Unit): Disposable
}

/**
 * Runs [listener] each time [observable] goes from no readers to one — see
 * [ObservationHooks]. Accepts any observable, so it also works on what
 * [observableRef], [computedStruct] and friends return.
 *
 * @throws IllegalArgumentException if [observable] does not track its readers
 */
fun onBecomeObserved(observable: Observable, listener: () -> Unit): Disposable =
    hooksOf(observable).onBecomeObserved(listener)

/**
 * Runs [listener] each time [observable] goes from some readers to none — see
 * [ObservationHooks].
 *
 * @throws IllegalArgumentException if [observable] does not track its readers
 */
fun onBecomeUnobserved(observable: Observable, listener: () -> Unit): Disposable =
    hooksOf(observable).onBecomeUnobserved(listener)

private fun hooksOf(observable: Observable): ObservationHooks =
    requireNotNull(observable as? ObservationHooks) {
        "${observable::class.simpleName} does not track its readers; observation hooks are available on " +
                "atoms, computeds, observable lists and sets, and the wrappers built on them"
    }

/** Something whose loss of readers is only acted on once [Tracking] says the current pass is over. */
internal interface PendingUnobservation {
    fun processUnobservation()
}

/**
 * The listeners of one observable, and whether it is currently observed as far
 * as they are concerned. Created only when the first listener is added, so the
 * many atoms nobody hooks into carry no extra state.
 */
internal class ObservationListeners(private val owner: Any, initiallyObserved: Boolean) {

    private val observed = mutableListOf<() -> Unit>()
    private val unobserved = mutableListOf<() -> Unit>()

    private var isBeingObserved = initiallyObserved

    fun addObserved(listener: () -> Unit): Disposable = add(observed, listener)

    fun addUnobserved(listener: () -> Unit): Disposable = add(unobserved, listener)

    /** The owner has a reader again — fires only if it was unobserved before. */
    fun becameObserved() {
        if (isBeingObserved) return
        isBeingObserved = true
        fire(observed)
    }

    /** The owner has no readers left — fires only if it was observed before. */
    fun becameUnobserved() {
        if (!isBeingObserved) return
        isBeingObserved = false
        fire(unobserved)
    }

    private fun add(listeners: MutableList<() -> Unit>, listener: () -> Unit): Disposable {
        listeners.add(listener)
        return object : Disposable {
            override fun dispose() {
                listeners.remove(listener)
            }
        }
    }

    private fun fire(listeners: List<() -> Unit>) {
        for (listener in listeners.toList()) {
            try {
                untracked(listener)
            } catch (error: Throwable) {
                reportUncaught(owner, error, null)
            }
        }
    }
}

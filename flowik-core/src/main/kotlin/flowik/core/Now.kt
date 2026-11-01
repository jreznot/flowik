package flowik.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The current time, as an observable — mobx-utils' `now(interval)`.
 *
 * Read inside a reaction or a computed, it makes the reader re-run every
 * [interval], which is all a clock, a countdown or an "edited 5 minutes ago"
 * label needs:
 *
 * ```kotlin
 * Label { "Edited ${Duration.between(note.edited, now(1.minutes)).toMinutes()} min ago" }
 * ```
 *
 * The timer runs only while something observes it: it starts with the first
 * reader and stops once the last one is gone. Readers asking for the same
 * [interval], [mainContext] and [clock] share one timer, so a hundred labels
 * cost one tick. A reader arriving between two ticks gets the current time, not
 * that of the last tick.
 *
 * Read outside any reaction, it simply returns `clock.instant()`.
 *
 * Ticks are written on [mainContext] — the thread that owns the observables.
 * The default, [Dispatchers.Main], is the Swing EDT; on Vaadin pass the UI's
 * dispatcher (`ui.asCoroutineDispatcher()`), which also gives each UI a timer
 * of its own.
 *
 * @param interval    how often readers re-run
 * @param mainContext where the ticks are written
 * @param clock       the time source — a fixed or offset clock in tests
 */
fun now(
    interval: Duration = 1.seconds,
    mainContext: CoroutineContext = Dispatchers.Main,
    clock: Clock = Clock.systemUTC(),
): Instant {
    require(interval.isPositive()) { "interval must be positive, was $interval" }
    if (Tracking.current == null) return clock.instant()
    val key = TickerKey(interval, mainContext, clock)
    val ticker = synchronized(tickers) { tickers.getOrPut(key) { Ticker(key) } }
    return ticker.read()
}

private data class TickerKey(val interval: Duration, val mainContext: CoroutineContext, val clock: Clock)

/**
 * The live tickers. Synchronized rather than thread-confined: on Vaadin every
 * UI reads from its own request threads, each with a ticker of its own.
 */
private val tickers = HashMap<TickerKey, Ticker>()

private class Ticker(private val key: TickerKey) {

    private val atom = ObservableValue(key.clock.instant(), name = "now(${key.interval})")

    private var job: Job? = null

    init {
        atom.onBecomeObserved { start() }
        atom.onBecomeUnobserved {
            job?.cancel()
            job = null
            synchronized(tickers) { tickers.remove(key, this) }
        }
    }

    fun read(): Instant {
        // Not ticking yet: catch up first. Nobody observes the atom, so the
        // write notifies nobody — not even the reader, who registers just below.
        if (atom.observerCount == 0) atom.value = key.clock.instant()
        return atom.value
    }

    private fun start() {
        job = CoroutineScope(key.mainContext).launch {
            while (isActive) {
                delay(key.interval)
                atom.value = key.clock.instant()
            }
        }
    }
}

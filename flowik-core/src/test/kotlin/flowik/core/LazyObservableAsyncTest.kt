@file:OptIn(ExperimentalCoroutinesApi::class)

package flowik.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The coroutine flavour: the first read launches the computation under the
 * configured scope, pushes are applied as actions on `mainContext`, and only one
 * run is ever in flight.
 *
 * The computations live in `backgroundScope` — they are the kind that need not
 * finish on their own — so the tests drive the clock with [settle] rather than
 * `advanceUntilIdle`, which by design leaves background work alone.
 */
class LazyObservableAsyncTest {

    @AfterTest
    fun tearDownMain() = Dispatchers.resetMain()

    /** Runs everything the scheduler has queued up to [ahead] into the future. */
    private fun TestScope.settle(ahead: Duration = 1.seconds) {
        advanceTimeBy(ahead)
        runCurrent()
    }

    @Test
    fun `the coroutine does not start until the value is read`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var invocations = 0
        val data = lazyObservableAsync("initial", backgroundScope) { sink ->
            invocations++
            sink("loaded")
        }

        settle()
        assertEquals(0, invocations)

        assertEquals("initial", data.current(), "the launch has not been dispatched yet")
        assertTrue(data.pending.value)

        settle()
        assertEquals(1, invocations)
        assertEquals("loaded", data.current())
        assertFalse(data.pending.value)
    }

    @Test
    fun `pushes arrive progressively and each one notifies`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val data = lazyObservableAsync(emptyList<String>(), backgroundScope) { sink ->
            delay(100)
            sink(listOf("first"))
            delay(100)
            sink(listOf("first", "second"))
        }
        val seen = mutableListOf<List<String>>()
        autoRun { seen += data.current() }

        settle(150.milliseconds)
        assertEquals(listOf(emptyList(), listOf("first")), seen)
        assertFalse(data.pending.value, "the first push ends pending")

        settle()
        assertEquals(listOf(emptyList(), listOf("first"), listOf("first", "second")), seen)
    }

    @Test
    fun `a push updates the value and pending in one action`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val data = lazyObservableAsync("initial", backgroundScope) { sink -> sink("loaded") }
        var runs = 0

        autoRun {
            runs++
            data.current()
            data.pending.value
        }
        assertEquals(1, runs)

        settle()
        assertEquals("loaded", data.current())
        assertEquals(2, runs, "value and pending change together, so the reaction re-runs once")
    }

    @Test
    fun `refresh cancels the run in flight and restarts it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var starts = 0
        val data = lazyObservableAsync("initial", backgroundScope) { sink ->
            val run = ++starts
            delay(100)
            sink("run$run")
        }

        data.current()
        settle(50.milliseconds)

        data.refresh()
        settle()

        assertEquals(2, starts)
        assertEquals("run2", data.current(), "the cancelled run never pushed")
    }

    @Test
    fun `dispose cancels the run and settles pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val data = lazyObservableAsync("initial", backgroundScope) { sink ->
            delay(100)
            sink("loaded")
        }

        data.current()
        assertTrue(data.pending.value)

        data.dispose()
        settle()

        assertEquals("initial", data.current())
        assertFalse(data.pending.value)
    }

    @Test
    fun `a failing coroutine reports to onError and stops pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val errors = mutableListOf<Throwable>()
        val data = lazyObservableAsync("initial", backgroundScope, onError = { errors += it }) {
            delay(10)
            error("boom")
        }

        data.current()
        settle()

        assertEquals(listOf("boom"), errors.map { it.message })
        assertEquals("initial", data.current())
        assertFalse(data.pending.value)
    }

    @Test
    fun `the body runs on the configured context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val worker = StandardTestDispatcher(testScheduler, name = "worker")
        var bodyDispatcher: ContinuationInterceptor? = null

        val data = lazyObservableAsync("initial", backgroundScope, context = worker) { sink ->
            bodyDispatcher = currentCoroutineContext()[ContinuationInterceptor]
            sink("loaded")
        }

        data.current()
        settle()

        assertSame(worker, bodyDispatcher, "the body ran on $bodyDispatcher")
        assertEquals("loaded", data.current())
    }

    @Test
    fun `a flow is exposed as a lazy observable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val source = flow {
            emit(1)
            gate.await()
            emit(2)
        }
        val data = source.toLazyObservable(0, backgroundScope)

        assertEquals(0, data.current())
        settle()
        assertEquals(1, data.current())

        gate.complete(Unit)
        settle()
        assertEquals(2, data.current())
    }
}

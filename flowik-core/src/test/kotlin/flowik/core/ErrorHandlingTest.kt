package flowik.core

import flowik.core.testing.LogRecorder
import org.slf4j.event.Level
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * MobX semantics for failing reactions: an exception is reported to `onError`,
 * or logged when no handler was given, and never re-thrown to the writer.
 */
class ErrorHandlingTest {

    @BeforeTest
    fun clearLog() = LogRecorder.clear()

    // autoRun

    @Test
    fun `autoRun reports an error thrown on the very first run`() {
        val errors = mutableListOf<Throwable>()

        autoRun(onError = { errors += it }) { error("boom") }

        assertEquals(listOf("boom"), errors.map { it.message })
    }

    @Test
    fun `autoRun reports an error thrown on a later run and keeps running`() {
        val count = observable(0)
        val errors = mutableListOf<Throwable>()
        var runs = 0

        autoRun(onError = { errors += it }) {
            runs++
            if (count.value == 1) error("boom")
        }
        assertEquals(1, runs)

        count.value = 1
        assertEquals(2, runs)
        assertEquals(listOf("boom"), errors.map { it.message })

        // Tracking survives the failure — the autoRun recovers on the next change
        count.value = 2
        assertEquals(3, runs)
        assertEquals(1, errors.size)
    }

    @Test
    fun `autoRun logs the error when no handler is given`() {
        val count = observable(0)

        autoRun("failing") { if (count.value > 0) error("boom") }

        count.value = 1

        val event = LogRecorder.events.single()
        assertEquals("flowik.core", event.logger)
        assertEquals(Level.ERROR, event.level)
        assertEquals("Uncaught exception in AutoRun(failing)", event.message)
        assertEquals("boom", event.error?.message)
    }

    // reaction

    @Test
    fun `reaction reports an error thrown by supply`() {
        val count = observable(0)
        val errors = mutableListOf<Throwable>()
        var received: Int? = null

        reaction(supply = { if (count.value == 1) error("boom") else count.value }, onError = { errors += it }) {
            received = it
        }

        count.value = 1
        assertEquals(listOf("boom"), errors.map { it.message })
        assertNull(received)

        count.value = 2
        assertEquals(2, received)
        assertEquals(1, errors.size)
    }

    @Test
    fun `reaction reports an error thrown by effect`() {
        val count = observable(0)
        val errors = mutableListOf<Throwable>()
        val seen = mutableListOf<Int>()

        reaction(supply = { count.value }, onError = { errors += it }) {
            seen += it
            if (it == 1) error("boom")
        }

        count.value = 1
        assertEquals(listOf(1), seen)
        assertEquals(listOf("boom"), errors.map { it.message })

        count.value = 2
        assertEquals(listOf(1, 2), seen)
        assertEquals(1, errors.size)
    }

    @Test
    fun `reaction whose creation-time supply failed fires the effect on the next success`() {
        val count = observable(0)
        var received: Int? = null

        reaction(supply = { if (count.value == 0) error("no data yet") else count.value }, onError = {}) {
            received = it
        }
        assertNull(received)

        count.value = 5
        assertEquals(5, received)
    }

    @Test
    fun `reaction logs the error when no handler is given`() {
        val count = observable(0)

        reaction("failing", supply = { count.value }) { error("boom") }
        count.value = 1

        val event = LogRecorder.events.single()
        assertEquals("Uncaught exception in Reaction(failing)", event.message)
        assertEquals("boom", event.error?.message)
    }

    // whenThen

    @Test
    fun `whenThen reports an error thrown by the predicate and stays armed`() {
        val count = observable(0)
        val errors = mutableListOf<Throwable>()
        var fired = false

        whenThen(check = { if (count.value == 1) error("boom") else count.value >= 2 }, onError = { errors += it }) {
            fired = true
        }

        count.value = 1
        assertEquals(listOf("boom"), errors.map { it.message })
        assertFalse(fired)

        count.value = 2
        assertTrue(fired, "whenThen should still fire after the predicate recovered")
    }

    @Test
    fun `whenThen reports an error thrown by the effect`() {
        val errors = mutableListOf<Throwable>()

        whenThen(check = { true }, onError = { errors += it }) { error("boom") }

        assertEquals(listOf("boom"), errors.map { it.message })
    }

    @Test
    fun `whenThen logs the error when no handler is given`() {
        whenThen("failing", check = { true }) { error("boom") }

        val event = LogRecorder.events.single()
        assertEquals("Uncaught exception in When(failing)", event.message)
        assertEquals("boom", event.error?.message)
    }

    // Isolation and handler failures

    @Test
    fun `a failing reaction does not prevent the others scheduled in the same batch`() {
        val count = observable(0)
        var secondRuns = 0

        autoRun("failing") { if (count.value > 0) error("boom") }
        autoRun("healthy") {
            count.value
            secondRuns++
        }

        action { count.value = 1 }

        assertEquals(2, secondRuns, "The healthy autoRun must run even though the first one threw")
        assertEquals(1, LogRecorder.events.size)
    }

    @Test
    fun `an onError handler that throws is logged instead of propagating`() {
        val count = observable(0)

        autoRun("failing", onError = { throw IllegalStateException("handler failed") }) {
            if (count.value > 0) error("boom")
        }

        count.value = 1

        val event = LogRecorder.events.single()
        assertEquals("Error handler of AutoRun(failing) failed", event.message)
        assertEquals("handler failed", event.error?.message)
        assertEquals(listOf("boom"), event.error?.suppressed?.map { it.message })
    }

    @Test
    fun `CancellationException is re-thrown rather than swallowed`() {
        val count = observable(0)

        autoRun(onError = { fail("onError must not see a cancellation") }) {
            if (count.value > 0) throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> { count.value = 1 }
        assertTrue(LogRecorder.events.isEmpty())
    }
}

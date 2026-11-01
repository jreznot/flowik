@file:OptIn(ExperimentalCoroutinesApi::class)

package flowik.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class LazyObservableErrorTest {

    @Test
    fun `a failing computation exposes its error and stops pending`() {
        var attempts = 0
        val data = lazyObservable(0, onError = {}) { sink ->
            if (++attempts == 1) error("offline")
            sink(42)
        }
        val seen = mutableListOf<String>()
        autoRun { seen += "${data.value} ${data.pending.value} ${data.error.value?.message}" }

        assertEquals("offline", data.error.value?.message)
        assertFalse(data.pending.value)

        data.refresh()

        assertNull(data.error.value)
        assertEquals(42, data.value)
        // The first read ran the computation, which failed on the spot.
        assertEquals("0 false offline", seen.first())
        assertEquals("42 false null", seen.last())
    }

    @Test
    fun `reset clears the error`() {
        val data = lazyObservable(0, onError = {}) { error("offline") }
        data.value

        data.reset()

        assertNull(data.error.value)
    }

    @Test
    fun `the coroutine flavour exposes its error too`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val data = lazyObservableAsync(0, backgroundScope, context = main, mainContext = main, onError = {}) {
            delay(1.seconds)
            error("timeout")
        }
        val errors = mutableListOf<String?>()
        autoRun { errors += data.error.value?.message; data.value }

        advanceTimeBy(2.seconds)
        runCurrent()

        assertEquals(listOf(null, "timeout"), errors)
        assertFalse(data.pending.value)
    }
}

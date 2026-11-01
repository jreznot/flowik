@file:OptIn(ExperimentalCoroutinesApi::class)

package flowik.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AwaitUntilTest {

    @Test
    fun `returns at once when the predicate already holds`() = runTest {
        val ready = observable(true)
        assertTrue(awaitUntil { ready.value })
    }

    @Test
    fun `suspends until the predicate holds`() = runTest {
        val count = observable(0)
        val waiting = async { awaitUntil { count.value >= 2 } }
        runCurrent()

        count.value = 1
        runCurrent()
        assertFalse(waiting.isCompleted)

        count.value = 2
        runCurrent()
        assertTrue(waiting.await())
        assertEquals(0, count.observerCount, "the reaction is gone")
    }

    @Test
    fun `returns false when the timeout elapses first, releasing the predicate`() = runTest {
        val ready = observable(false)

        assertFalse(awaitUntil(timeout = 5.seconds) { ready.value })
        assertEquals(0, ready.observerCount)
    }

    @Test
    fun `returns true when the predicate holds before the timeout`() = runTest {
        val ready = observable(false)
        val waiting = async { awaitUntil(timeout = 5.seconds) { ready.value } }
        runCurrent()

        ready.value = true
        runCurrent()

        assertTrue(waiting.await())
    }

    @Test
    fun `cancelling the caller releases the predicate`() = runTest {
        val ready = observable(false)
        val waiting = async { awaitUntil { ready.value } }
        runCurrent()
        assertEquals(1, ready.observerCount)

        waiting.cancel()
        runCurrent()

        assertEquals(0, ready.observerCount)
    }

    @Test
    fun `an exception thrown by the predicate ends the wait`() = runTest {
        val count = observable(0)
        var failure: Throwable? = null
        launch {
            failure = runCatching { awaitUntil { check(count.value != 1) { "boom" }; false } }.exceptionOrNull()
        }
        runCurrent()

        count.value = 1
        runCurrent()

        assertEquals("boom", assertIs<IllegalStateException>(failure).message)
        assertEquals(0, count.observerCount)
    }
}

@file:OptIn(ExperimentalCoroutinesApi::class)

package flowik.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** MobX's reaction options: change detection on the supplied value, fireImmediately, equals, delay. */
class ReactionOptionsTest {

    @Test
    fun `effect runs only when the supplied value changes`() {
        val count = observable(0)
        val seen = mutableListOf<Boolean>()
        reaction(supply = { count.value > 5 }) { seen += it }

        (1..5).forEach { count.value = it }
        assertEquals(emptyList(), seen, "still false — no effect")

        count.value = 6
        count.value = 7
        count.value = 2
        assertEquals(listOf(true, false), seen)
    }

    @Test
    fun `fireImmediately runs the effect on creation`() {
        val name = observable("a")
        val seen = mutableListOf<String>()

        reaction(supply = { name.value }, fireImmediately = true) { seen += it }
        name.value = "b"

        assertEquals(listOf("a", "b"), seen)
    }

    @Test
    fun `a custom comparer decides what counts as a change`() {
        val text = observable("hello")
        val seen = mutableListOf<String>()
        reaction(supply = { text.value }, equals = Comparer<String> { a, b -> a.equals(b, ignoreCase = true) }) {
            seen += it
        }

        text.value = "HELLO"
        text.value = "world"

        assertEquals(listOf("world"), seen)
    }

    @Test
    fun `a comparer that never matches runs the effect on every invalidation`() {
        val count = observable(0)
        var runs = 0
        reaction(supply = { count.value > 5 }, equals = Comparer { _, _ -> false }) { runs++ }

        count.value = 1
        count.value = 2

        assertEquals(2, runs)
    }

    @Test
    fun `the comparer sees the value of the last successful run`() {
        val count = observable(0)
        val seen = mutableListOf<Int>()
        reaction(supply = { count.value.also { check(it != 1) } }, onError = {}) { seen += it }

        count.value = 1 // supply fails — the last good value stays 0
        count.value = 0 // equal to it — no effect
        count.value = 2

        assertEquals(listOf(2), seen)
    }

    @Test
    fun `a delayed reaction coalesces changes and runs once with the latest value`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val count = observable(0)
        val seen = mutableListOf<Int>()
        reaction(supply = { count.value }, delay = 100.milliseconds, mainContext = dispatcher) { seen += it }

        count.value = 1
        count.value = 2
        advanceTimeBy(50.milliseconds)
        count.value = 3
        assertEquals(emptyList(), seen)

        advanceTimeBy(51.milliseconds)
        runCurrent()
        assertEquals(listOf(3), seen)

        count.value = 4
        advanceTimeBy(101.milliseconds)
        runCurrent()
        assertEquals(listOf(3, 4), seen)
    }

    @Test
    fun `disposing a delayed reaction cancels the pending run`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val count = observable(0)
        val seen = mutableListOf<Int>()
        val r = reaction(supply = { count.value }, delay = 100.milliseconds, mainContext = dispatcher) { seen += it }

        count.value = 1
        r.dispose()
        advanceTimeBy(200.milliseconds)
        runCurrent()

        assertEquals(emptyList(), seen)
    }

    @Test
    fun `a delayed run that finds the value back where it was does not run the effect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val count = observable(0)
        val seen = mutableListOf<Int>()
        reaction(supply = { count.value }, delay = 100.milliseconds, mainContext = dispatcher) { seen += it }

        count.value = 1
        count.value = 0
        advanceTimeBy(101.milliseconds)
        runCurrent()

        assertEquals(emptyList(), seen)
    }

    @Test
    fun `Bindings reaction takes the same options`() {
        val bindings = Bindings()
        val count = observable(0)
        val seen = mutableListOf<Boolean>()
        bindings.reaction(supply = { count.value > 1 }, fireImmediately = true) { seen += it }

        count.value = 1
        count.value = 2
        bindings.dispose()
        count.value = 0

        assertEquals(listOf(false, true), seen)
    }
}

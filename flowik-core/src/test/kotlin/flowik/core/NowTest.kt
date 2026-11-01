@file:OptIn(ExperimentalCoroutinesApi::class)

package flowik.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NowTest {

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    private val t0 = Instant.parse("2026-11-01T10:00:00Z")

    @Test
    fun `outside a reaction it is simply the clock`() {
        val clock = MutableClock(t0)
        assertEquals(t0, now(clock = clock))
    }

    @Test
    fun `a reader re-runs every interval while it observes`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val clock = MutableClock(t0)
        val seen = mutableListOf<Instant>()
        val r = autoRun { seen += now(1.seconds, main, clock) }

        clock.instant = t0.plusSeconds(1)
        advanceTimeBy(1001.milliseconds)
        clock.instant = t0.plusSeconds(2)
        advanceTimeBy(1000.milliseconds)
        runCurrent()
        assertEquals(listOf(t0, t0.plusSeconds(1), t0.plusSeconds(2)), seen)

        r.dispose()
        clock.instant = t0.plusSeconds(10)
        advanceTimeBy(5.seconds)
        runCurrent()
        assertEquals(3, seen.size, "the timer stopped with its last reader")
    }

    @Test
    fun `readers of the same interval share one timer`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val clock = MutableClock(t0)
        val first = mutableListOf<Instant>()
        val second = mutableListOf<Instant>()
        val a = autoRun { first += now(1.seconds, main, clock) }
        clock.instant = t0.plusMillis(500)
        val b = autoRun { second += now(1.seconds, main, clock) }

        clock.instant = t0.plusSeconds(1)
        advanceTimeBy(1001.milliseconds)
        runCurrent()

        // The second reader joined a running timer: it got the last tick, then ticks with it.
        assertEquals(listOf(t0, t0.plusSeconds(1)), first)
        assertEquals(listOf(t0, t0.plusSeconds(1)), second)
        a.dispose()
        b.dispose()
    }

    @Test
    fun `a reader arriving after a pause gets the current time`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        val clock = MutableClock(t0)
        autoRun { now(1.seconds, main, clock) }.dispose()

        clock.instant = t0.plusSeconds(30)
        val seen = mutableListOf<Instant>()
        val r = autoRun { seen += now(1.seconds, main, clock) }

        assertEquals(listOf(t0.plusSeconds(30)), seen)
        r.dispose()
    }
}

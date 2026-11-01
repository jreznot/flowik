package flowik.core

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimeValuesTest {

    private data class Session(val started: Instant, val timeout: Duration)

    private class Clock {
        var now by observable(Instant.parse("2026-11-01T10:00:00Z"), name = "now")
        var shift by observable(Duration.ZERO, name = "shift")
    }

    @Test
    fun `an Instant is a value, not an entity`() {
        val instant: ObservableValue<Instant> = observable(Instant.EPOCH)
        assertIs<ObservableValue<Instant>>(instant)
        assertEquals(Instant.EPOCH, instant.value)
    }

    @Test
    fun `a Duration is a value, not an entity`() {
        val duration: ObservableValue<Duration> = observable(Duration.ofMinutes(10))
        assertIs<ObservableValue<Duration>>(duration)
        assertEquals(Duration.ofMinutes(10), duration.value)
    }

    @Test
    fun `an equal Instant or Duration does not notify`() {
        val instant = observable(Instant.parse("2026-11-01T10:00:00Z"))
        val duration = observable(Duration.ofSeconds(60))
        val runs = mutableListOf<String>()
        autoRun { runs += "${instant.value} ${duration.value}" }

        // Equal but distinct instances — compared with equals, so nothing re-runs.
        instant.value = Instant.parse("2026-11-01T10:00:00Z")
        duration.value = Duration.ofMinutes(1)
        assertEquals(1, runs.size)

        instant.value = instant.value + Duration.ofDays(1)
        duration.value = Duration.ofMinutes(2)
        assertEquals(
            listOf("2026-11-01T10:00:00Z PT1M", "2026-11-02T10:00:00Z PT1M", "2026-11-02T10:00:00Z PT2M"),
            runs
        )
    }

    @Test
    fun `both work as property delegates`() {
        val clock = Clock()
        val seen = mutableListOf<Instant>()
        autoRun { seen += clock.now + clock.shift }

        action {
            clock.shift += Duration.ofDays(1)
            clock.now += Duration.ofMinutes(5)
        }

        assertEquals(listOf(Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-02T10:05:00Z")), seen)
        assertIs<MutableObservable<Duration>>(clock::shift.asObservable())
    }

    @Test
    fun `entity properties of both types are atoms`() {
        val session = observable(Session(Instant.EPOCH, Duration.ofMinutes(30)))
        val timeouts = mutableListOf<Duration>()
        autoRun { timeouts += session[Session::timeout] }

        session[Session::started] = Instant.EPOCH.plusSeconds(1)
        session[Session::timeout] = Duration.ofMinutes(30)
        session[Session::timeout] = Duration.ofHours(1)

        assertEquals(listOf(Duration.ofMinutes(30), Duration.ofHours(1)), timeouts)
    }
}

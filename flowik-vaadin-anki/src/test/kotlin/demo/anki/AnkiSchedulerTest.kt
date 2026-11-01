package demo.anki

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AnkiSchedulerTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-01T10:00:00Z")
    private val scheduler = Scheduler()

    private fun startOfDay(daysFromNow: Long) = now.atZone(zone).toLocalDate().plusDays(daysFromNow).atStartOfDay(zone).toInstant()

    private fun labels(card: Schedule) =
        scheduler.preview(card, now, zone).mapValues { scheduler.intervalLabel(it.value, now) }

    @Test
    fun `a new card goes through the learning steps`() {
        val new = Schedule()

        val again = scheduler.answer(new, Answer.AGAIN, now, zone)
        assertEquals(CardState.LEARNING, again.state)
        assertEquals(now + Duration.ofMinutes(1), again.due)
        assertEquals(2.5, again.ease)

        // Hard on the first step waits halfway to the second one.
        assertEquals(now + Duration.ofSeconds(330), scheduler.answer(new, Answer.HARD, now, zone).due)

        val good = scheduler.answer(new, Answer.GOOD, now, zone)
        assertEquals(CardState.LEARNING, good.state)
        assertEquals(1, good.step)
        assertEquals(now + Duration.ofMinutes(10), good.due)

        val graduated = scheduler.answer(good, Answer.GOOD, now, zone)
        assertEquals(CardState.REVIEW, graduated.state)
        assertEquals(1, graduated.intervalDays)
        assertEquals(startOfDay(1), graduated.due)
        assertEquals(2, graduated.reps)

        val easy = scheduler.answer(new, Answer.EASY, now, zone)
        assertEquals(CardState.REVIEW, easy.state)
        assertEquals(4, easy.intervalDays)

        assertEquals(mapOf(Answer.AGAIN to "1m", Answer.HARD to "6m", Answer.GOOD to "10m", Answer.EASY to "4d"), labels(new))
    }

    @Test
    fun `a review card grows its interval by its ease`() {
        val review = Schedule(CardState.REVIEW, due = startOfDay(0), intervalDays = 10, ease = 2.5, reps = 4)

        val hard = scheduler.answer(review, Answer.HARD, now, zone)
        assertEquals(12, hard.intervalDays)
        assertEquals(2.35, hard.ease, 1e-9)

        val good = scheduler.answer(review, Answer.GOOD, now, zone)
        assertEquals(25, good.intervalDays)
        assertEquals(2.5, good.ease)
        assertEquals(startOfDay(25), good.due)

        val easy = scheduler.answer(review, Answer.EASY, now, zone)
        assertEquals(32, easy.intervalDays)
        assertEquals(2.65, easy.ease, 1e-9)
    }

    @Test
    fun `a late review counts the days it was remembered for`() {
        val late = Schedule(CardState.REVIEW, due = startOfDay(-4), intervalDays = 10, ease = 2.5)
        assertEquals(30, scheduler.answer(late, Answer.GOOD, now, zone).intervalDays)
    }

    @Test
    fun `a forgotten review card is relearned with a lower ease`() {
        val review = Schedule(CardState.REVIEW, due = startOfDay(0), intervalDays = 10, ease = 2.5)

        val lapsed = scheduler.answer(review, Answer.AGAIN, now, zone)
        assertEquals(CardState.RELEARNING, lapsed.state)
        assertEquals(now + Duration.ofMinutes(10), lapsed.due)
        assertEquals(2.3, lapsed.ease, 1e-9)
        assertEquals(1, lapsed.lapses)
        assertEquals(1, lapsed.intervalDays)

        val relearned = scheduler.answer(lapsed, Answer.GOOD, now, zone)
        assertEquals(CardState.REVIEW, relearned.state)
        assertEquals(1, relearned.intervalDays)
        assertEquals(2.3, relearned.ease, 1e-9)
    }

    @Test
    fun `the ease never drops below its minimum`() {
        val struggling = Schedule(CardState.REVIEW, due = startOfDay(0), intervalDays = 3, ease = 1.35)
        assertEquals(1.3, scheduler.answer(struggling, Answer.AGAIN, now, zone).ease)
    }

    @Test
    fun `intervals are labelled the way Anki labels them`() {
        assertEquals("<1m", formatDuration(Duration.ofSeconds(20)))
        assertEquals("10m", formatDuration(Duration.ofMinutes(10)))
        assertEquals("1.5h", formatDuration(Duration.ofMinutes(90)))
        assertEquals("3d", formatDays(3))
        assertEquals("2mo", formatDays(60))
        assertEquals("1.5y", formatDays(548))
    }
}

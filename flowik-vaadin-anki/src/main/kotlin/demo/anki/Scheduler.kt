package demo.anki

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Anki's deck options that drive the scheduler, with Anki's defaults.
 *
 * @param learnAhead how far ahead a learning card may be shown when nothing
 *                   else is left to study — Anki's "learn ahead limit"
 */
data class SchedulerOptions(
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val graduatingIntervalDays: Int = 1,
    val easyIntervalDays: Int = 4,
    val startingEase: Double = 2.5,
    val minimumEase: Double = 1.3,
    val easyBonus: Double = 1.3,
    val hardMultiplier: Double = 1.2,
    val intervalModifier: Double = 1.0,
    val lapseIntervalMultiplier: Double = 0.0,
    val minimumIntervalDays: Int = 1,
    val maximumIntervalDays: Int = 36500,
    val learnAhead: Duration = Duration.ofMinutes(20)
)

/**
 * The SM-2 scheduler Anki used before FSRS, as described in its manual: new
 * cards go through the learning steps, graduate to day-based reviews whose
 * interval grows by the card's ease, and a forgotten review card goes back
 * through the relearning steps with a reduced interval and ease.
 *
 * It is a pure function of a [Schedule] and the time of the answer, so it has
 * no state of its own and nothing reactive about it. Interval fuzz is left out
 * to keep the button labels predictable.
 */
class Scheduler(val options: SchedulerOptions = SchedulerOptions()) {

    /** The schedule [card] gets when answered with [answer] at [now]. */
    fun answer(card: Schedule, answer: Answer, now: Instant, zone: ZoneId): Schedule {
        val next = when (card.state) {
            CardState.NEW, CardState.LEARNING -> stepAnswer(card, answer, now, zone, relearning = false)
            CardState.RELEARNING -> stepAnswer(card, answer, now, zone, relearning = true)
            CardState.REVIEW -> reviewAnswer(card, answer, now, zone)
        }
        return next.copy(reps = card.reps + 1)
    }

    /** What each answer button would do — the intervals shown above them. */
    fun preview(card: Schedule, now: Instant, zone: ZoneId): Map<Answer, Schedule> =
        Answer.entries.associateWith { answer(card, it, now, zone) }

    /** The label Anki shows above an answer button: `10m`, `1d`, `3.2mo`. */
    fun intervalLabel(next: Schedule, now: Instant): String =
        if (next.state == CardState.REVIEW) formatDays(next.intervalDays)
        else formatDuration(Duration.between(now, next.due))

    private fun stepAnswer(card: Schedule, answer: Answer, now: Instant, zone: ZoneId, relearning: Boolean): Schedule {
        val steps = if (relearning) options.relearningSteps else options.learningSteps
        val state = if (relearning) CardState.RELEARNING else CardState.LEARNING
        // The ease is given when the card is first seen, and kept from then on.
        val base = if (card.state == CardState.NEW) card.copy(ease = options.startingEase) else card

        // A relearning card graduates back to the interval it lapsed to.
        val goodInterval = if (relearning) card.intervalDays else options.graduatingIntervalDays
        val easyInterval = if (relearning) card.intervalDays + 1 else options.easyIntervalDays
        if (steps.isEmpty()) return graduate(base, if (answer == Answer.EASY) easyInterval else goodInterval, now, zone)

        val step = card.step.coerceIn(0, steps.lastIndex)
        return when (answer) {
            Answer.AGAIN -> base.copy(state = state, step = 0, due = now + steps[0])
            Answer.HARD -> base.copy(state = state, step = step, due = now + hardDelay(steps, step))
            Answer.GOOD ->
                if (step + 1 < steps.size) base.copy(state = state, step = step + 1, due = now + steps[step + 1])
                else graduate(base, goodInterval, now, zone)

            Answer.EASY -> graduate(base, easyInterval, now, zone)
        }
    }

    /**
     * Hard repeats the current step — except on the first one, where it waits
     * halfway to the second step, or half as long again when there is only one.
     */
    private fun hardDelay(steps: List<Duration>, step: Int): Duration = when {
        step > 0 -> steps[step]
        steps.size > 1 -> steps[0].plus(steps[1]).dividedBy(2)
        else -> minOf(Duration.ofMillis((steps[0].toMillis() * 1.5).roundToLong()), steps[0].plusDays(1))
    }

    private fun reviewAnswer(card: Schedule, answer: Answer, now: Instant, zone: ZoneId): Schedule {
        val interval = card.intervalDays
        val ease = card.ease
        // A card answered late counts the extra days it was remembered for.
        val daysLate = max(0L, ChronoUnit.DAYS.between(localDate(card.due, zone), localDate(now, zone))).toInt()

        val hardInterval = constrain(interval * options.hardMultiplier, previous = interval)
        val goodInterval = constrain((interval + daysLate / 2) * ease, previous = hardInterval)
        val easyInterval = constrain((interval + daysLate) * ease * options.easyBonus, previous = goodInterval)

        return when (answer) {
            Answer.AGAIN -> {
                val lapsed = card.copy(
                    ease = max(options.minimumEase, ease - 0.2),
                    lapses = card.lapses + 1,
                    intervalDays = max(options.minimumIntervalDays, (interval * options.lapseIntervalMultiplier).toInt())
                )
                val steps = options.relearningSteps
                if (steps.isEmpty()) graduate(lapsed, lapsed.intervalDays, now, zone)
                else lapsed.copy(state = CardState.RELEARNING, step = 0, due = now + steps[0])
            }

            Answer.HARD -> graduate(card.copy(ease = max(options.minimumEase, ease - 0.15)), hardInterval, now, zone)
            Answer.GOOD -> graduate(card, goodInterval, now, zone)
            Answer.EASY -> graduate(card.copy(ease = ease + 0.15), easyInterval, now, zone)
        }
    }

    /** Applies the interval modifier and keeps the interval above [previous] and within bounds. */
    private fun constrain(interval: Double, previous: Int): Int =
        max((interval * options.intervalModifier).toInt(), previous + 1)
            .coerceIn(options.minimumIntervalDays, options.maximumIntervalDays)

    private fun graduate(card: Schedule, days: Int, now: Instant, zone: ZoneId): Schedule {
        val interval = days.coerceIn(options.minimumIntervalDays, options.maximumIntervalDays)
        return card.copy(
            state = CardState.REVIEW,
            step = 0,
            intervalDays = interval,
            due = localDate(now, zone).plusDays(interval.toLong()).atStartOfDay(zone).toInstant()
        )
    }
}

fun localDate(instant: Instant, zone: ZoneId): LocalDate = LocalDate.ofInstant(instant, zone)

/** `<1m`, `10m`, `2h`, or a day count for anything longer — Anki's short form. */
fun formatDuration(duration: Duration): String {
    val minutes = (duration.seconds / 60.0).roundToLong()
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> trimZero(minutes / 60.0) + "h"
        else -> formatDays(duration.toDays().toInt())
    }
}

/** `4d`, `1.5mo`, `2.1y`. */
fun formatDays(days: Int): String = when {
    days < 30 -> "${days}d"
    days < 365 -> trimZero(days / 30.0) + "mo"
    else -> trimZero(days / 365.0) + "y"
}

private fun trimZero(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")

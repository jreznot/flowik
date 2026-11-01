package demo.vaadin.anki.backend

import demo.vaadin.anki.Answer
import demo.vaadin.anki.Review
import demo.vaadin.anki.Schedule
import java.time.Instant

/**
 * The review log. It only grows, so it is read by time range rather than
 * whole: the app needs the reviews of today for its limits and nothing older.
 */
interface ReviewService {

    /** Reviews answered at or after [time], oldest first. */
    fun findSince(time: Instant): List<Review>

    /** Gives the card its [schedule] after [answer], and logs the review with the schedule it replaced. */
    fun record(cardId: Long, answer: Answer, time: Instant, schedule: Schedule): Review

    /** Takes the review back: removes it from the log and restores the card's schedule from before it. */
    fun undo(reviewId: Long)
}

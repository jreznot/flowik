package demo.anki.backend

import demo.anki.CardState
import demo.anki.Schedule
import demo.anki.localDate
import java.time.Clock
import java.time.Duration

private val CAPITALS = listOf(
    "France" to "Paris",
    "Germany" to "Berlin",
    "Italy" to "Rome",
    "Spain" to "Madrid",
    "Portugal" to "Lisbon",
    "Netherlands" to "Amsterdam",
    "Austria" to "Vienna",
    "Czechia" to "Prague",
    "Poland" to "Warsaw",
    "Hungary" to "Budapest",
    "Sweden" to "Stockholm",
    "Norway" to "Oslo",
    "Finland" to "Helsinki",
    "Denmark" to "Copenhagen",
    "Ireland" to "Dublin",
    "Greece" to "Athens",
    "Croatia" to "Zagreb",
    "Slovenia" to "Ljubljana",
    "Estonia" to "Tallinn",
    "Latvia" to "Riga",
    "Lithuania" to "Vilnius",
    "Iceland" to "Reykjavík"
)

private val SPANISH = listOf(
    "hello" to "hola",
    "thank you" to "gracias",
    "please" to "por favor",
    "goodbye" to "adiós",
    "water" to "el agua",
    "bread" to "el pan",
    "house" to "la casa",
    "dog" to "el perro",
    "cat" to "el gato",
    "book" to "el libro",
    "friend" to "el amigo / la amiga",
    "to eat" to "comer",
    "to drink" to "beber",
    "to speak" to "hablar",
    "to live" to "vivir"
)

private val FLOWIK = listOf(
    "Create a mutable atom" to "observable(value)",
    "Derive a cached value" to "computed { … }",
    "Run a side effect whenever what it reads changes" to "autoRun { … }",
    "Batch writes so reactions run once" to "action { … }",
    "React only to what a supply function returns" to "reaction(supply) { effect }",
    "Run an effect once, when a condition becomes true" to "whenThen(check) { effect }",
    "Read observables without subscribing" to "untracked { … }",
    "A list of data classes with per-property atoms" to "observables<T>()",
    "An atom compared by identity" to "observableRef(value)",
    "Buffer edits until OK is pressed" to "createViewModel(entity)",
    "Swap a child component on a value change (Vaadin)" to "childSwitch(observe) { … }",
    "Keep children in step with a list (Vaadin)" to "items(list) { … }"
)

/**
 * Fills a fresh database with three decks. Part of one is already under review,
 * with cards due today and tomorrow, so the counts are not all "new" at start
 * and skipping a day has something to show.
 */
internal fun seedSampleDecks(db: InMemoryAnkiDatabase, clock: Clock) {
    val now = clock.instant()
    val zone = clock.zone
    val today = localDate(now, zone)

    fun reviewed(daysFromToday: Long, interval: Int, ease: Double = 2.5, reps: Int = 3) = Schedule(
        state = CardState.REVIEW,
        due = today.plusDays(daysFromToday).atStartOfDay(zone).toInstant(),
        intervalDays = interval,
        ease = ease,
        reps = reps
    )

    db.insertDeck("Capitals of Europe", "Countries on the front, their capitals on the back.").let { deck ->
        CAPITALS.forEach { (country, capital) ->
            db.insertCard(deck.id, "What is the capital of $country?", capital, Schedule())
        }
    }

    db.insertDeck("Spanish Basics", "Everyday words. The first half is already being reviewed.").let { deck ->
        val schedules = listOf(
            reviewed(-3, 4), reviewed(-1, 6, ease = 2.35), reviewed(0, 3), reviewed(0, 10, ease = 2.65, reps = 5),
            reviewed(0, 2, reps = 2), reviewed(1, 5), reviewed(1, 3), reviewed(2, 8, reps = 4),
            Schedule(
                state = CardState.RELEARNING,
                due = now - Duration.ofMinutes(2),
                intervalDays = 1,
                ease = 2.3,
                reps = 6,
                lapses = 1
            )
        )
        SPANISH.forEachIndexed { index, (english, spanish) ->
            db.insertCard(deck.id, english, spanish, schedules.getOrElse(index) { Schedule() })
        }
    }

    db.insertDeck("Flowik API", "The library this demo is built with.").let { deck ->
        FLOWIK.forEach { (question, answer) -> db.insertCard(deck.id, question, answer, Schedule()) }
    }
}

package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComputedFnTest {

    private data class Card(val deck: String, val front: String, val due: Boolean = false)

    @Test
    fun `each key is computed once while observed`() {
        val factor = observable(2)
        val evaluations = mutableListOf<Int>()
        val times = computedFn { n: Int -> evaluations += n; n * factor.value }

        autoRun { times(1); times(2) }
        autoRun { times(1) }

        assertEquals(listOf(1, 2), evaluations)
        assertEquals(setOf(1, 2), times.cachedKeys)
    }

    @Test
    fun `a change recomputes only the keys that read it`() {
        val cards = observables(Card("spanish", "hola"), Card("capitals", "Paris"))
        val evaluations = mutableListOf<String>()
        val dueIn = computedFn { deck: String ->
            evaluations += deck
            cards.items.filter { it.snapshot.deck == deck }.count { it[Card::due] }
        }
        val seen = mutableListOf<String>()
        autoRun { seen += "spanish=${dueIn("spanish")}" }
        autoRun { seen += "capitals=${dueIn("capitals")}" }
        evaluations.clear()

        cards[0][Card::due] = true

        assertEquals(listOf("spanish"), evaluations)
        assertEquals(listOf("spanish=0", "capitals=0", "spanish=1"), seen)
    }

    @Test
    fun `an entry is dropped once nothing observes it, and its sources with it`() {
        val factor = observable(2)
        val times = computedFn { n: Int -> n * factor.value }
        val run = autoRun { times(3) }
        assertEquals(1, factor.observerCount)

        run.dispose()

        assertTrue(times.cachedKeys.isEmpty())
        assertEquals(0, factor.observerCount)
    }

    @Test
    fun `an entry swapped to another reader within an action is kept`() {
        var evaluations = 0
        val square = computedFn { n: Int -> evaluations++; n * n }
        val first = autoRun { square(4) }

        action {
            first.dispose()
            autoRun { square(4) }
        }

        assertEquals(1, evaluations)
        assertEquals(setOf(4), square.cachedKeys)
    }

    @Test
    fun `an untracked call evaluates without caching, unless the key is cached already`() {
        var evaluations = 0
        val square = computedFn { n: Int -> evaluations++; n * n }

        assertEquals(9, square(3))
        assertEquals(9, square(3))
        assertEquals(2, evaluations)
        assertTrue(square.cachedKeys.isEmpty())

        autoRun { square(3) }
        assertEquals(9, square(3))
        assertEquals(3, evaluations, "served from the entry the reaction keeps")
    }

    @Test
    fun `keepAlive keeps entries cached and current until disposed`() {
        val factor = observable(2)
        var evaluations = 0
        val times = computedFn(keepAlive = true) { n: Int -> evaluations++; n * factor.value }

        assertEquals(6, times(3))
        assertEquals(6, times(3))
        assertEquals(1, evaluations)

        factor.value = 10
        assertEquals(30, times(3))

        times.dispose()
        assertTrue(times.cachedKeys.isEmpty())
        assertEquals(0, factor.observerCount)
    }

    @Test
    fun `with a comparer an entry notifies only when its result changes`() {
        val cards = observables(Card("spanish", "hola"), Card("spanish", "gato"))
        val fronts = computedFn(equals = Comparer.Structural) { deck: String ->
            cards.items.filter { it.snapshot.deck == deck }.map { it.snapshot.front }
        }
        var runs = 0
        autoRun { runs++; fronts("spanish") }

        cards.add(Card("capitals", "Paris"))
        assertEquals(1, runs, "the Spanish fronts did not change")

        cards.add(Card("spanish", "perro"))
        assertEquals(2, runs)
    }

    @Test
    fun `the two-argument form caches per pair`() {
        var evaluations = 0
        val sum = computedFn { a: Int, b: Int -> evaluations++; a + b }

        autoRun { sum(1, 2); sum(1, 2); sum(2, 1) }

        assertEquals(2, evaluations)
    }
}

package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyComputedTest {

    @Test
    fun `plain computed re-runs dependents even when its value is unchanged`() {
        val count = observable(0)
        val isBig = computed { count.value > 5 }
        var runs = 0

        autoRun { isBig.value; runs++ }
        assertEquals(1, runs)

        count.value = 1
        assertEquals(2, runs, "invalidation propagates unconditionally — the gap computedStruct closes")
    }

    @Test
    fun `computedStruct notifies only when the derived value changes`() {
        val count = observable(0)
        val isBig = computedStruct { count.value > 5 }
        var runs = 0

        autoRun { isBig.value; runs++ }
        assertEquals(1, runs)

        count.value = 1
        count.value = 2
        count.value = 5
        assertEquals(1, runs, "still false")

        count.value = 6
        assertEquals(2, runs)

        count.value = 7
        assertEquals(2, runs, "still true")
    }

    @Test
    fun `computedStruct compares list results structurally`() {
        val source = ObservableList(listOf("a", "b"))
        val sorted = computedStruct { source.items.sorted() }
        var runs = 0

        autoRun { sorted.value; runs++ }
        assertEquals(1, runs)

        source.setAll(listOf("b", "a"))
        assertEquals(1, runs, "same sorted content")

        source.add("c")
        assertEquals(2, runs)
        assertEquals(listOf("a", "b", "c"), sorted.value)
    }

    @Test
    fun `computedRef compares results by identity`() {
        val flag = observable(false)
        val yes = "yes"
        val no = "no"
        val label = computedRef { if (flag.value) yes else no }
        var runs = 0

        autoRun { label.value; runs++ }

        flag.value = true
        assertEquals(2, runs)
        assertEquals("yes", label.value)
    }

    @Test
    fun `an action re-evaluates the derivation once`() {
        val a = observable(1)
        val b = observable(2)
        var computations = 0
        val sum = computedStruct { computations++; a.value + b.value }
        var runs = 0

        autoRun { sum.value; runs++ }
        assertEquals(1, computations, "evaluated eagerly on creation")
        assertEquals(1, runs)

        action {
            a.value = 10
            b.value = 20
        }

        assertEquals(2, computations, "one re-evaluation for the whole action")
        assertEquals(2, runs, "one re-run for the whole action")
        assertEquals(30, sum.value)
    }

    @Test
    fun `an action whose writes cancel out notifies nobody`() {
        val count = observable(0)
        val sum = computedStruct { count.value }
        var runs = 0

        autoRun { sum.value; runs++ }

        action {
            count.value = 5
            count.value = 0
        }

        assertEquals(1, runs)
    }

    @Test
    fun `reading inside an action sees the fresh value`() {
        val count = observable(1)
        val doubled = computedStruct { count.value * 2 }
        var seen = -1

        action {
            count.value = 5
            seen = doubled.value
        }

        assertEquals(10, seen)
    }

    @Test
    fun `chained policy computeds filter each other`() {
        val count = observable(0)
        val bucket = computedStruct { count.value / 10 }
        val label = computedStruct { "bucket ${bucket.value}" }
        val seen = mutableListOf<String>()

        autoRun { seen.add(label.value) }
        assertEquals(listOf("bucket 0"), seen)

        count.value = 5
        assertEquals(listOf("bucket 0"), seen, "same bucket")

        count.value = 10
        assertEquals(listOf("bucket 0", "bucket 1"), seen)

        action { count.value = 25 }
        assertEquals(listOf("bucket 0", "bucket 1", "bucket 2"), seen)
    }

    @Test
    fun `derived list ops compose on a policy computed`() {
        val source = ObservableList(listOf(1, 2, 3, 4))
        val evens = computedStruct { source.items.filter { it % 2 == 0 } }
        val labels = evens.map { "n$it" }

        assertEquals(listOf("n2", "n4"), labels.value)

        source.add(6)
        assertEquals(listOf("n2", "n4", "n6"), labels.value)
    }

    @Test
    fun `subscribers are notified only on change`() {
        val count = observable(0)
        val isBig = computedStruct { count.value > 5 }
        var changes = 0
        isBig.subscribe { changes++ }

        count.value = 1
        assertEquals(0, changes)

        count.value = 10
        assertEquals(1, changes)
    }

    @Test
    fun `dispose stops observing the wrapped computation`() {
        val count = observable(0)
        val doubled = computedStruct { count.value * 2 }
        var runs = 0

        autoRun { doubled.value; runs++ }
        count.value = 1
        assertEquals(2, runs)

        doubled.dispose()
        count.value = 100

        assertEquals(2, runs)
        assertEquals(2, doubled.value, "value is frozen at the last observed result")
    }
}

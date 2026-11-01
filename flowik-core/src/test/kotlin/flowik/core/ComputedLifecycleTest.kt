package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ComputedLifecycleTest {

    @Test
    fun `a disposed reaction releases the computed, and the computed its sources`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }
        val run = autoRun { doubled.value }
        assertEquals(1, doubled.observerCount)
        assertEquals(1, count.observerCount)

        run.dispose()

        assertEquals(0, doubled.observerCount)
        assertEquals(0, count.observerCount, "a suspended computed no longer subscribes to its sources")
    }

    @Test
    fun `a suspended computed reconnects on the next tracked read`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }
        autoRun { doubled.value }.dispose()

        val seen = mutableListOf<Int>()
        autoRun { seen += doubled.value }
        count.value = 3

        assertEquals(listOf(2, 6), seen)
    }

    @Test
    fun `an unobserved computed evaluates on every read and keeps nothing`() {
        val count = observable(1)
        var evaluations = 0
        val doubled = computed { evaluations++; count.value * 2 }

        assertEquals(2, doubled.value)
        assertEquals(2, doubled.value)

        assertEquals(2, evaluations)
        assertEquals(0, count.observerCount)
    }

    @Test
    fun `an observed computed is cached, for untracked reads too`() {
        val count = observable(1)
        var evaluations = 0
        val doubled = computed { evaluations++; count.value * 2 }
        autoRun { doubled.value }

        assertEquals(2, doubled.value)
        assertEquals(2, untracked { doubled.value })
        assertEquals(1, evaluations)

        count.value = 2
        assertEquals(4, doubled.value)
        assertEquals(2, evaluations)
    }

    @Test
    fun `a chain of computeds is released all the way up`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }
        val label = computed { "x${doubled.value}" }
        val run = autoRun { label.value }
        assertEquals(1, doubled.observerCount)

        run.dispose()

        assertEquals(0, label.observerCount)
        assertEquals(0, doubled.observerCount)
        assertEquals(0, count.observerCount)
    }

    @Test
    fun `a computed read on every run is not recomputed when its reader re-runs`() {
        val count = observable(1)
        val other = observable("a")
        var evaluations = 0
        val doubled = computed { evaluations++; count.value * 2 }
        var runs = 0
        autoRun { runs++; doubled.value; other.value }

        other.value = "b"

        assertEquals(2, runs)
        assertEquals(1, evaluations, "still observed throughout the re-run, so still cached")
    }

    @Test
    fun `a computed the reader stops reading is released after the run`() {
        val useFirst = observable(true)
        val first = computed { "first" }
        val second = computed { "second" }
        autoRun { if (useFirst.value) first.value else second.value }

        useFirst.value = false

        assertEquals(0, first.observerCount)
        assertEquals(1, second.observerCount)
    }

    @Test
    fun `suspension waits for the end of the flush, so a rebuilt reader finds the value cached`() {
        // The shape of a UI container swapping its child: the old child's
        // binding is disposed and the new child's reads the same computed.
        val count = observable(1)
        var evaluations = 0
        val doubled = computed { evaluations++; count.value * 2 }
        val selection = observable(0)
        var child: Disposable = autoRun { doubled.value }
        autoRun {
            selection.value
            untracked {
                child.dispose()
                child = autoRun { doubled.value }
            }
        }
        val before = evaluations

        action { selection.value = 1 }

        assertEquals(before, evaluations)
        assertEquals(1, doubled.observerCount)
        assertEquals(1, count.observerCount)
    }

    @Test
    fun `a computed left unobserved by an action is suspended when it ends`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }
        val run = autoRun { doubled.value }

        action {
            run.dispose()
            assertEquals(1, count.observerCount, "still connected inside the action")
        }

        assertEquals(0, count.observerCount)
    }

    @Test
    fun `a reaction and a when release the computeds they read`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }

        val react = reaction(supply = { doubled.value }) {}
        var fired = false
        whenThen(check = { doubled.value > 5 }) { fired = true }
        assertEquals(2, doubled.observerCount)

        count.value = 3
        assertEquals(true, fired)
        assertEquals(1, doubled.observerCount, "a when disposes itself once it fires")

        react.dispose()
        assertEquals(0, doubled.observerCount)
        assertEquals(0, count.observerCount)
    }

    @Test
    fun `disposing a computedStruct releases its sources`() {
        val count = observable(1)
        val doubled = computedStruct { count.value * 2 }
        assertEquals(1, count.observerCount)

        doubled.dispose()

        assertEquals(0, count.observerCount)
    }
}

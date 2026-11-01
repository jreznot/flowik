package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ObservationHooksTest {

    private fun ObservationHooks.record(events: MutableList<String>, name: String = "") {
        onBecomeObserved { events += "${name}observed" }
        onBecomeUnobserved { events += "${name}unobserved" }
    }

    @Test
    fun `an atom is observed while a reaction reads it`() {
        val count = observable(0)
        val events = mutableListOf<String>()
        count.record(events)

        val run = autoRun { count.value }
        assertEquals(listOf("observed"), events)

        run.dispose()
        assertEquals(listOf("observed", "unobserved"), events)
    }

    @Test
    fun `a reaction re-running does not flicker its atoms`() {
        val count = observable(0)
        val events = mutableListOf<String>()
        count.record(events)
        autoRun { count.value }

        count.value = 1
        count.value = 2

        assertEquals(listOf("observed"), events)
    }

    @Test
    fun `only the first reader and the last one count`() {
        val count = observable(0)
        val events = mutableListOf<String>()
        count.record(events)

        val first = autoRun { count.value }
        val second = autoRun { count.value }
        first.dispose()
        assertEquals(listOf("observed"), events)

        second.dispose()
        assertEquals(listOf("observed", "unobserved"), events)
    }

    @Test
    fun `a reader swapped within an action fires nothing`() {
        val count = observable(0)
        val events = mutableListOf<String>()
        val first = autoRun { count.value }
        count.record(events)

        action {
            first.dispose()
            autoRun { count.value }
        }

        assertEquals(emptyList(), events)
    }

    @Test
    fun `a computed is observed by reactions and subscribers, and its sources follow it`() {
        val count = observable(1)
        val doubled = computed { count.value * 2 }
        val events = mutableListOf<String>()
        count.record(events, "count ")
        doubled.record(events, "doubled ")

        val run = autoRun { doubled.value }
        assertEquals(listOf("doubled observed", "count observed"), events)

        val subscription = doubled.subscribe {}
        run.dispose()
        assertEquals(2, events.size, "the subscriber still observes it")

        subscription.dispose()
        assertEquals(listOf("doubled observed", "count observed", "doubled unobserved", "count unobserved"), events)
    }

    @Test
    fun `wrappers report through the free functions`() {
        val list = ObservableList(listOf(1, 2))
        val ref = observableRef("a")
        val events = mutableListOf<String>()
        onBecomeObserved(list) { events += "list observed" }
        onBecomeUnobserved(ref) { events += "ref unobserved" }

        autoRun { list.size; ref.value }.dispose()

        assertEquals(listOf("list observed", "ref unobserved"), events)
        assertFailsWith<IllegalArgumentException> { onBecomeObserved(observable(Task("x"))) {} }
    }

    @Test
    fun `listeners run untracked, and a disposed listener stops`() {
        val count = observable(0)
        val other = observable("x")
        var observedRuns = 0
        val listener = count.onBecomeObserved { observedRuns++; other.value }

        var runs = 0
        autoRun { runs++; count.value }
        other.value = "y"
        assertEquals(1, runs, "what the listener read did not become a dependency")

        listener.dispose()
        val again = observable(0).also { it.onBecomeObserved { observedRuns++ } }
        autoRun { again.value }
        autoRun { count.value }
        assertEquals(2, observedRuns)
    }

    @Test
    fun `a failing listener is logged and does not stop the others`() {
        val count = observable(0)
        var reached = false
        count.onBecomeObserved { error("boom") }
        count.onBecomeObserved { reached = true }

        autoRun { count.value }

        assertEquals(true, reached)
    }

    private data class Task(val title: String)
}

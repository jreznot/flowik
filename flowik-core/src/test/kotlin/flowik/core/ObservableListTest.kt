package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObservableListTest {

    private data class Task(val title: String, val done: Boolean = false)

    @Test
    fun `adding to an entity list returns the entity it holds`() {
        val tasks = observables(Task("first"))

        val added = tasks.add(Task("second"))
        val inserted = tasks.add(0, Task("zeroth"))

        assertSame(added, tasks[2])
        assertSame(inserted, tasks[0])
        added[Task::done] = true
        assertTrue(tasks[2][Task::done])
    }

    @Test
    fun `removeAll removes each match with its own change, in one batch`() {
        val numbers = ObservableList(listOf(1, 2, 3, 4, 5, 6))
        val changes = mutableListOf<ListChange<Int>>()
        numbers.onChange { changes += it }
        var runs = 0
        autoRun { runs++; numbers.size }

        assertTrue(numbers.removeAll { it % 2 == 0 })

        assertEquals(listOf(1, 3, 5), numbers.items)
        // From the back, so every index is still valid when its change is applied.
        assertEquals(
            listOf<ListChange<Int>>(ListChange.Remove(5, 6), ListChange.Remove(3, 4), ListChange.Remove(1, 2)),
            changes
        )
        assertEquals(2, runs, "reactions run once for all the removals")
    }

    @Test
    fun `removeAll with no match changes nothing`() {
        val numbers = ObservableList(listOf(1, 3))
        var runs = 0
        autoRun { runs++; numbers.items }

        assertFalse(numbers.removeAll { it > 10 })

        assertEquals(1, runs)
    }

    @Test
    fun `removeAll on an entity list can match on element properties`() {
        val tasks = observables(Task("a"), Task("b"), Task("c"))
        tasks[1][Task::done] = true

        tasks.removeAll { it[Task::done] }

        assertEquals(listOf("a", "c"), tasks.items.map { it[Task::title] })
    }
}

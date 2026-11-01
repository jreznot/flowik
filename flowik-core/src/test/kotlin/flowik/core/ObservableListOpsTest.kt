package flowik.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** addAll, move, sort and the diffing setAll — and the change events they emit. */
class ObservableListOpsTest {

    private data class Task(val title: String, val done: Boolean = false)

    /** Records the events of [list] and replays them onto a copy, the way a bound container would. */
    private class Replica<T>(list: ObservableList<T>) {
        val changes = mutableListOf<ListChange<T>>()
        val items = list.items.toMutableList()

        init {
            list.onChange { change ->
                changes += change
                when (change) {
                    is ListChange.Insert -> items.add(change.index, change.item)
                    is ListChange.Remove -> assertEquals(change.item, items.removeAt(change.index))
                    is ListChange.Update -> assertEquals(change.old, items.set(change.index, change.new))
                    is ListChange.Move -> items.add(change.to, items.removeAt(change.from).also { assertEquals(change.item, it) })
                    is ListChange.Clear -> items.clear()
                }
            }
        }
    }

    @Test
    fun `addAll inserts every item with its own change, in one batch`() {
        val list = ObservableList(listOf("a", "d"))
        val replica = Replica(list)
        var runs = 0
        autoRun { runs++; list.size }

        assertTrue(list.addAll(1, listOf("b", "c")))
        assertTrue(list.addAll(listOf("e")))
        assertFalse(list.addAll(emptyList()))

        assertEquals(listOf("a", "b", "c", "d", "e"), list.items)
        assertEquals(list.items, replica.items)
        assertEquals(
            listOf<ListChange<String>>(ListChange.Insert(1, "b"), ListChange.Insert(2, "c"), ListChange.Insert(4, "e")),
            replica.changes
        )
        assertEquals(3, runs, "once on creation, once per addAll that added something")
    }

    @Test
    fun `addAll rejects an index out of range without changing anything`() {
        val list = ObservableList(listOf("a"))
        assertFailsWith<IndexOutOfBoundsException> { list.addAll(5, listOf("b")) }
        assertEquals(listOf("a"), list.items)
    }

    @Test
    fun `addAll of an entity list wraps the plain values`() {
        val tasks = observables(Task("a"))
        tasks.addAll(listOf(Task("b"), Task("c")))
        assertEquals(listOf("a", "b", "c"), tasks.items.map { it[Task::title] })
    }

    @Test
    fun `move emits a single Move and notifies readers`() {
        val list = ObservableList(listOf("a", "b", "c", "d"))
        val replica = Replica(list)
        val seen = mutableListOf<List<String>>()
        autoRun { seen += list.items }

        list.move(0, 2)
        list.move(3, 1)
        list.move(1, 1)

        assertEquals(listOf("b", "d", "c", "a"), list.items)
        assertEquals(list.items, replica.items)
        assertEquals(
            listOf<ListChange<String>>(ListChange.Move(0, 2, "a"), ListChange.Move(3, 1, "d")),
            replica.changes
        )
        assertEquals(3, seen.size)
    }

    @Test
    fun `move rejects indices out of range without changing anything`() {
        val list = ObservableList(listOf("a", "b"))
        assertFailsWith<IndexOutOfBoundsException> { list.move(0, 2) }
        assertFailsWith<IndexOutOfBoundsException> { list.move(-1, 0) }
        assertEquals(listOf("a", "b"), list.items)
    }

    @Test
    fun `sort reorders through moves, stably, in one batch`() {
        val list = ObservableList(listOf("pear", "fig", "apple", "kiwi", "date"))
        val replica = Replica(list)
        var runs = 0
        autoRun { runs++; list.items }

        list.sortBy { it.length }

        assertEquals(listOf("fig", "pear", "kiwi", "date", "apple"), list.items)
        assertEquals(list.items, replica.items)
        assertTrue(replica.changes.all { it is ListChange.Move })
        assertEquals(2, runs)
    }

    @Test
    fun `sorting a sorted list changes nothing`() {
        val list = ObservableList(listOf(1, 2, 3))
        val replica = Replica(list)

        list.sortWith(naturalOrder())
        list.sortByDescending { -it }

        assertTrue(replica.changes.isEmpty())
    }

    @Test
    fun `sort keeps the instances it moves`() {
        val tasks = observables(Task("b"), Task("a"))
        val a = tasks[1]

        tasks.sortBy { it[Task::title] }

        assertSame(a, tasks[0])
    }

    @Test
    fun `setAll keeps the items that stay and patches the rest`() {
        val list = ObservableList(listOf("a", "b", "c", "d"))
        val replica = Replica(list)

        list.setAll(listOf("a", "x", "c", "d", "e"))

        assertEquals(listOf("a", "x", "c", "d", "e"), list.items)
        assertEquals(list.items, replica.items)
        assertEquals(
            listOf<ListChange<String>>(ListChange.Update(1, "b", "x"), ListChange.Insert(4, "e")),
            replica.changes
        )
    }

    @Test
    fun `setAll removes just the items that left`() {
        val list = ObservableList((1..6).toList())
        val replica = Replica(list)

        list.setAll(listOf(1, 3, 5))

        assertEquals(listOf(1, 3, 5), replica.items)
        assertTrue(replica.changes.all { it is ListChange.Remove })
        assertEquals(3, replica.changes.size)
    }

    @Test
    fun `setAll keeps the instance of an equal item`() {
        val first = Task("a")
        val list = ObservableList(listOf(first))

        list.setAll(listOf(Task("a"), Task("b")))

        assertSame(first, list[0])
    }

    @Test
    fun `setAll runs reactions once`() {
        val list = ObservableList(listOf(1, 2, 3))
        var runs = 0
        autoRun { runs++; list.items }

        list.setAll(listOf(3, 2, 1, 0))
        list.setAll(listOf(3, 2, 1, 0))

        assertEquals(2, runs)
    }

    @Test
    fun `setAll accepts a view of the list itself`() {
        val list = ObservableList(listOf(1, 2, 3))
        list.setAll(list.value.subList(1, 3))
        assertEquals(listOf(2, 3), list.items)
    }

    @Test
    fun `setAll reproduces any target through its events`() {
        val random = Random(42)
        repeat(500) {
            val old = List(random.nextInt(0, 12)) { random.nextInt(0, 5) }
            val new = List(random.nextInt(0, 12)) { random.nextInt(0, 5) }
            val list = ObservableList(old)
            val replica = Replica(list)

            list.setAll(new)

            assertEquals(new, list.items, "$old -> $new")
            assertEquals(new, replica.items, "$old -> $new")
            assertTrue(replica.changes.none { it is ListChange.Clear }, "$old -> $new")
        }
    }

    @Test
    fun `setAll on a long list with little in common still gets there`() {
        val list = ObservableList((0 until 3000).toList())
        val replica = Replica(list)
        val target = (0 until 3000).map { if (it % 2 == 0) it else -it }

        list.setAll(target)

        assertEquals(target, replica.items)
    }

    @Test
    fun `the MutableList view batches addAll`() {
        val list = ObservableList(listOf("a"))
        var runs = 0
        autoRun { runs++; list.items }

        list.value.addAll(listOf("b", "c"))

        assertEquals(listOf("a", "b", "c"), list.items)
        assertEquals(2, runs)
    }
}

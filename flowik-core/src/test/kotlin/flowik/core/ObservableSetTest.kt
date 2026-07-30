package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservableSetTest {

    private class Store {
        var tags: MutableSet<String> by observableSet("new")
    }

    @Test
    fun `keeps insertion order and collapses duplicates`() {
        val roles = observableSet("admin", "user", "admin")

        assertEquals(listOf("admin", "user"), roles.items.toList())
        assertEquals(2, roles.size)
    }

    @Test
    fun `reactions re-run when an element is added or removed`() {
        val roles = observableSet<String>()
        val sizes = mutableListOf<Int>()

        autoRun { sizes.add(roles.size) }
        assertEquals(listOf(0), sizes)

        roles.add("admin")
        roles.remove("admin")

        assertEquals(listOf(0, 1, 0), sizes)
    }

    @Test
    fun `adding a duplicate is not a change`() {
        val roles = observableSet("admin")
        var runs = 0

        autoRun { roles.items; runs++ }
        assertEquals(1, runs)

        assertFalse(roles.add("admin"))
        assertEquals(1, runs, "the set already contained the element")

        assertTrue(roles.add("user"))
        assertEquals(2, runs)
    }

    @Test
    fun `removing an absent element is not a change`() {
        val roles = observableSet("admin")
        var runs = 0

        autoRun { roles.items; runs++ }

        assertFalse(roles.remove("user"))
        assertEquals(1, runs)
    }

    @Test
    fun `contains is reactive`() {
        val roles = observableSet<String>()
        val seen = mutableListOf<Boolean>()

        autoRun { seen.add("admin" in roles) }
        assertEquals(listOf(false), seen)

        roles.add("admin")
        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun `iteration and isEmpty are reactive`() {
        val roles = observableSet<String>()
        val seen = mutableListOf<String>()

        autoRun { seen.add(roles.joinToString(",")) }
        autoRun { roles.isEmpty() }

        roles.addAll(listOf("a", "b"))

        assertEquals(listOf("", "a,b"), seen)
        assertFalse(roles.isEmpty())
    }

    @Test
    fun `toggle adds then removes`() {
        val selection = observableSet<String>()

        assertTrue(selection.toggle("alice"))
        assertTrue("alice" in selection)

        assertFalse(selection.toggle("alice"))
        assertFalse("alice" in selection)
    }

    @Test
    fun `clear on an empty set notifies nobody`() {
        val roles = observableSet<String>()
        var runs = 0

        autoRun { roles.items; runs++ }

        roles.clear()
        assertEquals(1, runs)

        roles.add("admin")
        roles.clear()
        assertEquals(3, runs)
        assertEquals(emptySet(), roles.items)
    }

    @Test
    fun `addAll and removeAll are batched into one re-run`() {
        val roles = observableSet("a")
        var runs = 0

        autoRun { roles.items; runs++ }
        assertEquals(1, runs)

        roles.addAll(listOf("b", "c"))
        assertEquals(2, runs, "one re-run for the whole addAll")

        roles.removeAll(listOf("a", "b"))
        assertEquals(3, runs, "one re-run for the whole removeAll")
        assertEquals(setOf("c"), roles.items)
    }

    @Test
    fun `addAll reports whether anything was new`() {
        val roles = observableSet("a")

        assertFalse(roles.addAll(listOf("a")))
        assertTrue(roles.addAll(listOf("a", "b")))
        assertFalse(roles.removeAll(listOf("z")))
        assertTrue(roles.removeAll(listOf("a", "z")))
    }

    @Test
    fun `setAll replaces the contents in a single batch`() {
        val roles = observableSet("a", "b")
        var runs = 0

        autoRun { roles.items; runs++ }

        roles.setAll(listOf("b", "c"))
        assertEquals(setOf("b", "c"), roles.items)
        assertEquals(2, runs, "one re-run for the whole replacement")
    }

    @Test
    fun `setAll with equal contents changes nothing`() {
        val roles = observableSet("a", "b")
        var runs = 0
        val changes = mutableListOf<SetChange<String>>()

        autoRun { roles.items; runs++ }
        roles.onChange { changes.add(it) }

        roles.setAll(listOf("b", "a"))

        assertEquals(1, runs)
        assertEquals(emptyList(), changes)
    }

    @Test
    fun `emits fine-grained change events`() {
        val roles = observableSet("a")
        val changes = mutableListOf<SetChange<String>>()

        val subscription = roles.onChange { changes.add(it) }

        roles.add("b")
        roles.remove("a")
        roles.clear()

        assertEquals(
            listOf(
                SetChange.Add("b"),
                SetChange.Remove("a"),
                SetChange.Clear(setOf("b")),
            ),
            changes
        )

        subscription.dispose()
        roles.add("c")
        assertEquals(3, changes.size)
    }

    @Test
    fun `subscribers are notified on change only`() {
        val roles = observableSet("a")
        var changes = 0
        val subscription = roles.subscribe { changes++ }

        roles.add("a")
        assertEquals(0, changes)

        roles.add("b")
        assertEquals(1, changes)

        subscription.dispose()
        roles.add("c")
        assertEquals(1, changes)
    }

    @Test
    fun `writes are batched by action`() {
        val roles = observableSet<String>()
        var runs = 0

        autoRun { roles.size; runs++ }
        assertEquals(1, runs)

        action {
            roles.add("a")
            roles.add("b")
            roles.remove("a")
        }
        assertEquals(2, runs, "one re-run for the whole action")
    }

    @Test
    fun `computed derives from a set`() {
        val roles = observableSet("admin")
        val isAdmin = computed { "admin" in roles }
        val sorted = computed { roles.items.sorted() }

        assertTrue(isAdmin.value)
        assertEquals(listOf("admin"), sorted.value)

        roles.setAll(listOf("user", "guest"))

        assertFalse(isAdmin.value)
        assertEquals(listOf("guest", "user"), sorted.value)
    }

    @Test
    fun `map filter and flatMap return reactive derivations`() {
        val roles = observableSet("admin", "user")

        val upper = roles.map { it.uppercase() }
        val short = roles.filter { it.length <= 4 }
        val chars = roles.flatMap { it.take(2).toList() }

        assertEquals(listOf("ADMIN", "USER"), upper.value)
        assertEquals(listOf("user"), short.value)
        assertEquals(listOf('a', 'd', 'u', 's'), chars.value)

        roles.add("guest")

        assertEquals(listOf("ADMIN", "USER", "GUEST"), upper.value)
        assertEquals(listOf("user"), short.value)
    }

    @Test
    fun `set-shaped operations collapse duplicates`() {
        val roles = observableSet("admin", "user", "guest")

        val lengths = roles.mapToSet { it.length }
        val short = roles.filterToSet { it.length <= 5 }
        val snapshot = roles.toSet()

        assertEquals(setOf(5, 4), lengths.value)
        assertEquals(setOf("admin", "user", "guest"), short.value)
        assertEquals(setOf("admin", "user", "guest"), snapshot.value)

        roles.remove("guest")
        assertEquals(setOf("admin", "user"), snapshot.value)
    }

    @Test
    fun `derived list operations compose on top of a set derivation`() {
        val roles = observableSet("admin", "user")

        val initials = roles.map { it.uppercase() }.filter { it.startsWith("A") }

        assertEquals(listOf("ADMIN"), initials.value)

        roles.add("auditor")
        assertEquals(listOf("ADMIN", "AUDITOR"), initials.value)
    }

    @Test
    fun `items snapshot is detached from later mutations`() {
        val roles = observableSet("a")
        val snapshot = roles.items

        roles.add("b")

        assertEquals(setOf("a"), snapshot)
        assertEquals(setOf("a", "b"), roles.items)
    }

    @Test
    fun `works as a property delegate`() {
        val store = Store()
        val seen = mutableListOf<Set<String>>()

        autoRun { seen.add(store.tags.toSet()) }
        assertEquals(listOf(setOf("new")), seen)

        store.tags.add("urgent")
        assertEquals(listOf(setOf("new"), setOf("new", "urgent")), seen)

        store.tags.remove("new")
        assertEquals(setOf("urgent"), store.tags)

        store.tags = mutableSetOf("done")
        assertEquals(setOf("done"), store.tags)
        assertEquals(4, seen.size)
    }

    @Test
    fun `delegate view supports contains size and iterator removal`() {
        val store = Store()
        store.tags.addAll(listOf("urgent", "later"))

        assertTrue("urgent" in store.tags)
        assertEquals(3, store.tags.size)

        val iterator = store.tags.iterator()
        while (iterator.hasNext()) {
            if (iterator.next() == "later") iterator.remove()
        }

        assertEquals(setOf("new", "urgent"), store.tags)

        store.tags.clear()
        assertTrue(store.tags.isEmpty())
    }

    @Test
    fun `reads inside untracked do not subscribe`() {
        val roles = observableSet("a")
        var runs = 0

        autoRun { untracked { roles.items }; runs++ }

        roles.add("b")
        assertEquals(1, runs)
    }

    @Test
    fun `supplier get is tracked so one-way bindings re-run`() {
        val roles = observableSet("a")
        val seen = mutableListOf<Set<String>>()

        autoRun { seen.add(roles.get().toSet()) }

        roles.add("b")

        assertEquals(listOf(setOf("a"), setOf("a", "b")), seen)
    }
}

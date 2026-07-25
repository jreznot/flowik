package flowik.core

import kotlin.test.*

class PolicyObservableTest {

    private data class Point(val x: Int, val y: Int)

    private class Store {
        var session: String by observableRef("anonymous")
        var tags: List<String> by observableStruct(emptyList())
        var plain: String by ObservableValue("plain")
    }

    @Test
    fun `ref notifies when an equal but distinct instance is assigned`() {
        val point = observableRef(Point(1, 2))
        var runs = 0

        autoRun { point.value; runs++ }
        assertEquals(1, runs)

        point.value = Point(1, 2) // equal, different instance
        assertEquals(2, runs, "ref compares by identity, so this is a change")
    }

    @Test
    fun `ref ignores a write of the very same instance`() {
        val initial = Point(1, 2)
        val point = observableRef(initial)
        var runs = 0

        autoRun { point.value; runs++ }
        assertEquals(1, runs)

        point.value = initial
        assertEquals(1, runs)
    }

    @Test
    fun `ObservableValue ignores an equal instance - the difference ref makes`() {
        val point = ObservableValue(Point(1, 2))
        var runs = 0

        autoRun { point.value; runs++ }
        point.value = Point(1, 2)

        assertEquals(1, runs, "ObservableValue compares with equals")
    }

    @Test
    fun `ref keeps the value atomic instead of decomposing it`() {
        val original = Point(1, 2)
        val point = observableRef(original)

        assertSame(original, point.value)
    }

    @Test
    fun `struct ignores a structurally equal array`() {
        val matrix = observableStruct(arrayOf(intArrayOf(1, 2), intArrayOf(3)))
        var runs = 0

        autoRun { matrix.value; runs++ }
        assertEquals(1, runs)

        matrix.value = arrayOf(intArrayOf(1, 2), intArrayOf(3))
        assertEquals(1, runs, "arrays are compared deeply, so nothing changed")

        matrix.value = arrayOf(intArrayOf(1, 2), intArrayOf(4))
        assertEquals(2, runs)
    }

    @Test
    fun `ObservableValue notifies for an equal array - the difference struct makes`() {
        val numbers = ObservableValue(intArrayOf(1, 2))
        var runs = 0

        autoRun { numbers.value; runs++ }
        numbers.value = intArrayOf(1, 2)

        assertEquals(2, runs, "IntArray.equals is identity-based")
    }

    @Test
    fun `struct ignores an equal list and reacts to a different one`() {
        val tags = observableStruct(listOf("a", "b"))
        val observed = mutableListOf<List<String>>()

        autoRun { observed.add(tags.value) }

        tags.value = listOf("a", "b")
        assertEquals(1, observed.size)

        tags.value = listOf("a", "b", "c")
        assertEquals(listOf(listOf("a", "b"), listOf("a", "b", "c")), observed)
    }

    @Test
    fun `works as a property delegate`() {
        val store = Store()
        val seen = mutableListOf<String>()

        autoRun { seen.add(store.session) }
        assertEquals(listOf("anonymous"), seen)

        store.session = "alice"
        assertEquals(listOf("anonymous", "alice"), seen)

        store.tags = emptyList()
        store.tags = listOf("admin")
        assertEquals(listOf("admin"), store.tags)
    }

    @Test
    fun `writes are batched by action`() {
        val session = observableRef("a")
        val tags = observableStruct(listOf("x"))
        var runs = 0

        autoRun { session.value; tags.value; runs++ }
        assertEquals(1, runs)

        action {
            session.value = "b"
            tags.value = listOf("y")
        }
        assertEquals(2, runs, "one re-run for the whole action")
    }

    @Test
    fun `computed can derive from a policy observable`() {
        val session = observableRef("alice")
        val upper = computed { session.value.uppercase() }

        assertEquals("ALICE", upper.value)

        session.value = "bob"
        assertEquals("BOB", upper.value)
    }

    @Test
    fun `subscribers are notified on change only`() {
        val tags = observableStruct(listOf("a"))
        var changes = 0
        val subscription = tags.subscribe { changes++ }

        tags.value = listOf("a")
        assertEquals(0, changes)

        tags.value = listOf("b")
        assertEquals(1, changes)

        subscription.dispose()
        tags.value = listOf("c")
        assertEquals(1, changes)
    }

    @Test
    fun `unwrapBinding resolves both ObservableValue and observableRef delegates`() {
        val store = Store()

        val session: MutableObservable<String> = unwrapBinding(store::session)
        session.value = "carol"
        assertEquals("carol", store.session)

        val plain: MutableObservable<String> = unwrapBinding(store::plain)
        plain.value = "changed"
        assertEquals("changed", store.plain)
    }

    @Test
    fun `unwrapBinding reports a property that is not reactive`() {
        class NotReactive {
            val text: String by lazy { "x" }
        }

        val failure = assertFailsWith<IllegalArgumentException> { unwrapBinding(NotReactive()::text) }
        assertTrue(failure.message!!.contains("not a MutableObservable"), failure.message!!)
    }
}

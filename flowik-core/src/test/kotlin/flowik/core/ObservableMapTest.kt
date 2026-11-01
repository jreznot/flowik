package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservableMapTest {

    @Test
    fun `basic reads and writes`() {
        val map = observableMap(1 to "one", 2 to "two")

        map[3] = "three"
        assertEquals("one", map.put(1, "uno"))
        assertEquals("two", map.remove(2))
        assertNull(map.remove(42))

        assertEquals(mapOf(1 to "uno", 3 to "three"), map.items)
        assertEquals(setOf(1, 3), map.keys)
        assertEquals(listOf("uno", "three"), map.values)
        assertEquals(2, map.size)
        assertTrue(map.containsKey(3))
        assertFalse(map.containsKey(2))
    }

    @Test
    fun `a lookup re-runs only for its own key`() {
        val map = observableMap<String, Int>()
        val seen = mutableListOf<Int?>()
        autoRun { seen += map["a"] }

        map["b"] = 1
        map["a"] = 1
        map["a"] = 1 // equal — no change
        map["b"] = 2
        map.remove("a")

        assertEquals(listOf(null, 1, null), seen)
    }

    @Test
    fun `containsKey tracks additions and removals of its key`() {
        val map = observableMap<String, Int>()
        val seen = mutableListOf<Boolean>()
        autoRun { seen += map.containsKey("a") }

        map["a"] = 1
        map["z"] = 1
        map.clear()

        assertEquals(listOf(false, true, false), seen)
    }

    @Test
    fun `whole-map readers re-run on any change`() {
        val map = observableMap("a" to 1)
        val sizes = mutableListOf<Int>()
        autoRun { sizes += map.size }

        map["b"] = 2
        map["a"] = 5
        map.remove("b")

        assertEquals(listOf(1, 2, 2, 1), sizes)
    }

    @Test
    fun `a reader of both a key and the whole map runs once per write`() {
        val map = observableMap("a" to 1)
        var runs = 0
        autoRun { runs++; map["a"]; map.size }

        map["a"] = 2

        assertEquals(2, runs)
    }

    @Test
    fun `key atoms are dropped once nothing looks the key up`() {
        val map = observableMap<Int, String>()
        val r = autoRun { (1..10).forEach { map[it] } }
        r.dispose()

        map[1] = "x"

        assertEquals(mapOf(1 to "x"), map.items)
        // Reading outside a reaction creates no atom either.
        repeat(100) { map[it] }
    }

    @Test
    fun `change events and setAll`() {
        val map = observableMap("a" to 1, "b" to 2)
        val changes = mutableListOf<MapChange<String, Int>>()
        map.onChange { changes += it }
        var runs = 0
        autoRun { runs++; map.items }

        map.setAll(mapOf("b" to 3, "c" to 4))
        map.setAll(mapOf("b" to 3, "c" to 4))
        map.clear()

        assertEquals(
            listOf<MapChange<String, Int>>(
                MapChange.Remove("a", 1),
                MapChange.Update("b", 2, 3),
                MapChange.Add("c", 4),
                MapChange.Clear(mapOf("b" to 3, "c" to 4)),
            ),
            changes
        )
        assertEquals(3, runs)
    }

    @Test
    fun `works as a MutableMap property delegate`() {
        class Store {
            var drafts: MutableMap<Long, String> by observableMap()
        }
        val store = Store()
        val seen = mutableListOf<String?>()
        autoRun { seen += store.drafts[42L] }

        store.drafts[42L] = "hello"
        store.drafts.entries.first().setValue("bye")
        store.drafts.entries.iterator().apply { next(); remove() }
        store.drafts = mutableMapOf(42L to "again")

        assertEquals(listOf(null, "hello", "bye", null, "again"), seen)
    }

    @Test
    fun `iterates entries with destructuring`() {
        val map = observableMap("a" to 1, "b" to 2)
        val joined = buildString { for ((k, v) in map) append("$k=$v;") }
        assertEquals("a=1;b=2;", joined)
    }
}

class ObservableMapDelegateTrackingTest {

    @Test
    fun `a lookup through the delegated view still tracks its key only`() {
        class Store {
            val drafts: MutableMap<Long, String> by observableMap()
        }
        val store = Store()
        var runs = 0
        autoRun { runs++; store.drafts[42L] }

        store.drafts[7L] = "other"
        store.drafts[42L] = "mine"

        assertEquals(2, runs)
    }
}

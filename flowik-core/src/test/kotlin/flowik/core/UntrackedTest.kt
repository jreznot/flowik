package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals

class UntrackedTest {

    @Test
    fun `reads inside untracked do not become dependencies`() {
        val tracked = observable(0)
        val hidden = observable(0)
        var runs = 0

        autoRun {
            tracked.value
            untracked { hidden.value }
            runs++
        }
        assertEquals(1, runs)

        hidden.value = 1
        assertEquals(1, runs, "hidden was read without tracking")

        tracked.value = 1
        assertEquals(2, runs)
    }

    @Test
    fun `untracked returns the value of the block`() {
        val count = observable(7)
        assertEquals(14, untracked { count.value * 2 })
    }

    @Test
    fun `tracking is restored after untracked`() {
        val a = observable(0)
        val b = observable(0)
        var runs = 0

        autoRun {
            untracked { a.value }
            b.value
            runs++
        }

        a.value = 1
        assertEquals(1, runs)

        b.value = 1
        assertEquals(2, runs, "b is still tracked after the untracked block")
    }

    @Test
    fun `a computed created inside untracked still tracks its own dependencies`() {
        val count = observable(1)
        val doubled = untracked { computed { count.value * 2 } }

        assertEquals(2, doubled.value)
        count.value = 5
        assertEquals(10, doubled.value)
    }
}

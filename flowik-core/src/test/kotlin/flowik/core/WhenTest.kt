package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhenTest {

    @Test
    fun `fires immediately when predicate is already true`() {
        val flag = observable(true)
        var fired = false

        whenThen({ flag.value }) { fired = true }

        assertTrue(fired, "Effect should fire immediately when predicate is true")
    }

    @Test
    fun `fires when predicate becomes true`() {
        val count = observable(0)
        var fired = false

        whenThen({ count.value >= 3 }) { fired = true }

        assertEquals(false, fired)

        count.value = 1
        assertEquals(false, fired)

        count.value = 3
        assertTrue(fired, "Effect should fire once predicate returns true")
    }

    @Test
    fun `fires only once`() {
        val count = observable(0)
        var fireCount = 0

        whenThen({ count.value >= 2 }) { fireCount++ }

        count.value = 2
        assertEquals(1, fireCount)

        // Further changes should not re-fire
        count.value = 1
        count.value = 5
        assertEquals(1, fireCount, "Effect must fire only once")
    }

    @Test
    fun `does not fire after dispose`() {
        val count = observable(0)
        var fired = false

        val w = whenThen({ count.value >= 5 }) { fired = true }

        count.value = 2
        assertEquals(false, fired)

        w.dispose()

        count.value = 10
        assertEquals(false, fired, "Should not fire after dispose")
    }

    @Test
    fun `tracks computed dependencies`() {
        val price = observable(50)
        val tax = observable(10)
        val total = computed { price.value + tax.value }
        var fired = false

        whenThen({ total.value > 100 }) { fired = true }

        assertEquals(false, fired)

        price.value = 80
        assertEquals(false, fired)

        tax.value = 25
        assertTrue(fired, "Should fire when computed dependency crosses threshold")
    }

    @Test
    fun `works inside action batch`() {
        val a = observable(0)
        val b = observable(0)
        var fired = false

        whenThen({ a.value + b.value >= 10 }) { fired = true }

        action {
            a.value = 3
            b.value = 8
        }
        assertTrue(fired, "Should fire after action batch completes")
    }

    @Test
    fun `name is reflected in toString`() {
        val w = whenThen({ false }, name = "myWhen") {}
        assertEquals("When(myWhen)", w.toString())
    }

    @Test
    fun `anonymous when toString`() {
        val w = whenThen({ false }) {}
        assertEquals("When(anonymous)", w.toString())
    }
}

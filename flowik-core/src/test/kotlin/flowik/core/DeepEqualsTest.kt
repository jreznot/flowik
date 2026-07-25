package flowik.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeepEqualsTest {

    private data class WithArray(val values: IntArray)

    @Test
    fun `nulls and identity`() {
        assertTrue(deepEquals(null, null))
        assertFalse(deepEquals(null, 1))
        assertFalse(deepEquals(1, null))

        val instance = Any()
        assertTrue(deepEquals(instance, instance))
        assertFalse(deepEquals(Any(), Any()))
    }

    @Test
    fun `primitive arrays compare by content`() {
        assertTrue(deepEquals(intArrayOf(1, 2), intArrayOf(1, 2)))
        assertFalse(deepEquals(intArrayOf(1, 2), intArrayOf(2, 1)))
        assertTrue(deepEquals(byteArrayOf(1), byteArrayOf(1)))
        assertTrue(deepEquals(doubleArrayOf(1.5), doubleArrayOf(1.5)))
        assertTrue(deepEquals(booleanArrayOf(true, false), booleanArrayOf(true, false)))
        assertTrue(deepEquals(charArrayOf('a'), charArrayOf('a')))
        assertFalse(deepEquals(intArrayOf(1), longArrayOf(1)))
    }

    @Test
    fun `object arrays compare deeply`() {
        assertTrue(deepEquals(arrayOf("a", "b"), arrayOf("a", "b")))
        assertTrue(deepEquals(arrayOf(intArrayOf(1), intArrayOf(2)), arrayOf(intArrayOf(1), intArrayOf(2))))
        assertFalse(deepEquals(arrayOf(intArrayOf(1)), arrayOf(intArrayOf(2))))
        assertFalse(deepEquals(arrayOf("a"), arrayOf("a", "b")))
    }

    @Test
    fun `lists compare element-wise and recursively`() {
        assertTrue(deepEquals(listOf(1, 2), listOf(1, 2)))
        assertFalse(deepEquals(listOf(1, 2), listOf(2, 1)))
        assertTrue(deepEquals(listOf(intArrayOf(1)), listOf(intArrayOf(1))))
        assertTrue(deepEquals(listOf(listOf(intArrayOf(1))), listOf(listOf(intArrayOf(1)))))
        assertFalse(deepEquals(listOf(1), listOf(1, 2)))
    }

    @Test
    fun `maps compare keys and values recursively`() {
        assertTrue(deepEquals(mapOf("a" to intArrayOf(1)), mapOf("a" to intArrayOf(1))))
        assertFalse(deepEquals(mapOf("a" to intArrayOf(1)), mapOf("a" to intArrayOf(2))))
        assertFalse(deepEquals(mapOf("a" to 1), mapOf("b" to 1)))
        assertFalse(deepEquals(mapOf("a" to 1), mapOf("a" to 1, "b" to 2)))
        assertTrue(deepEquals(emptyMap<String, Int>(), emptyMap<String, Int>()))
    }

    @Test
    fun `everything else falls back to equals`() {
        assertTrue(deepEquals("a", "a"))
        assertTrue(deepEquals(setOf(1, 2), setOf(2, 1)))
        assertTrue(deepEquals(1, 1))
        assertFalse(deepEquals(1, 1L))

        // Documented limitation: no reflection into object properties, so a data
        // class holding an array keeps its own identity-based array comparison.
        assertFalse(deepEquals(WithArray(intArrayOf(1)), WithArray(intArrayOf(1))))
    }
}

package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoRunTest {

    @Test
    fun `runs immediately on creation`() {
        val count = observable(0)
        var observed = -1

        autoRun { observed = count.value }

        assertEquals(0, observed)
    }

    @Test
    fun `re-runs when observed dependency changes`() {
        val count = observable(0)
        val log = mutableListOf<Int>()

        autoRun { log.add(count.value) }

        assertEquals(listOf(0), log)

        count.value = 1
        assertEquals(listOf(0, 1), log)

        count.value = 2
        assertEquals(listOf(0, 1, 2), log)
    }

    @Test
    fun `does not re-run after dispose`() {
        val count = observable(0)
        val log = mutableListOf<Int>()

        val ar = autoRun { log.add(count.value) }
        assertEquals(listOf(0), log)

        ar.dispose()
        count.value = 1
        assertEquals(listOf(0), log, "Should not re-run after dispose")
    }

    @Test
    fun `batches updates inside action`() {
        val a = observable(1)
        val b = observable(2)
        var runCount = 0

        autoRun {
            a.value + b.value
            runCount++
        }
        assertEquals(1, runCount, "Initial run")

        action {
            a.value = 10
            b.value = 20
        }
        assertEquals(2, runCount, "Should fire only once after action completes")
    }

    @Test
    fun `tracks computed dependencies`() {
        val firstName = observable("John")
        val lastName = observable("Doe")
        val fullName = computed { "${firstName.value} ${lastName.value}" }
        val log = mutableListOf<String>()

        autoRun { log.add(fullName.value) }

        assertEquals(listOf("John Doe"), log)

        firstName.value = "Jane"
        assertEquals(listOf("John Doe", "Jane Doe"), log)

        lastName.value = "Smith"
        assertEquals(listOf("John Doe", "Jane Doe", "Jane Smith"), log)
    }

    @Test
    fun `re-tracks dependencies on each run`() {
        val toggle = observable(true)
        val a = observable("A")
        val b = observable("B")
        val log = mutableListOf<String>()

        autoRun {
            log.add(if (toggle.value) a.value else b.value)
        }
        assertEquals(listOf("A"), log)

        // Changing b should NOT trigger (not tracked while toggle=true)
        b.value = "B2"
        assertEquals(listOf("A"), log)

        // Flip toggle — now b is tracked, a is not
        toggle.value = false
        assertEquals(listOf("A", "B2"), log)

        // Now changing a should NOT trigger
        a.value = "A2"
        assertEquals(listOf("A", "B2"), log)

        // Changing b SHOULD trigger
        b.value = "B3"
        assertEquals(listOf("A", "B2", "B3"), log)
    }

    @Test
    fun `name is reflected in toString`() {
        val ar = autoRun("myEffect") {}
        assertEquals("AutoRun(myEffect)", ar.toString())
    }

    @Test
    fun `anonymous autoRun toString`() {
        val ar = autoRun {}
        assertEquals("AutoRun(anonymous)", ar.toString())
    }
}

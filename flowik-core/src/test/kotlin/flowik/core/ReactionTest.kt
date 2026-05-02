package flowik.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionTest {
    @Test
    fun `does not run effect on creation`() {
        val count = observable(0)
        var runCount = 0

        reaction(supply = { count.value }) { runCount++ }

        assertEquals(0, runCount)
    }

    @Test
    fun `runs effect when tracked dependency changes`() {
        val count = observable(0)
        val log = mutableListOf<Int>()

        reaction(supply = { count.value }) { log.add(it) }

        assertEquals(emptyList(), log)

        count.value = 1
        assertEquals(listOf(1), log)

        count.value = 2
        assertEquals(listOf(1, 2), log)
    }

    @Test
    fun `effect receives current value of data function`() {
        val name = observable("Alice")
        var received = ""

        reaction(supply = { name.value }) { received = it }

        name.value = "Bob"
        assertEquals("Bob", received)
    }

    @Test
    fun `does not re-run after dispose`() {
        val count = observable(0)
        val log = mutableListOf<Int>()

        val r = reaction(supply = { count.value }) { log.add(it) }

        count.value = 1
        assertEquals(listOf(1), log)

        r.dispose()
        count.value = 2
        assertEquals(listOf(1), log, "Should not re-run after dispose")
    }

    @Test
    fun `effect is not tracked - observables inside effect do not become dependencies`() {
        val trigger = observable(0)
        val sideData = observable("initial")
        var effectRunCount = 0

        reaction(supply = { trigger.value }) {
            effectRunCount++
            sideData.value // read inside effect — must NOT create a dependency
        }

        trigger.value = 1
        assertEquals(1, effectRunCount)

        // Changing sideData should NOT re-trigger the reaction
        sideData.value = "changed"
        assertEquals(1, effectRunCount, "Effect must not re-run on observables read inside it")
    }

    @Test
    fun `batches updates inside action`() {
        val a = observable(1)
        val b = observable(2)
        var runCount = 0

        reaction(supply = { a.value + b.value }) { runCount++ }
        assertEquals(0, runCount, "Must not fire on creation")

        action {
            a.value = 10
            b.value = 20
        }
        assertEquals(1, runCount, "Should fire exactly once after action completes")
    }

    @Test
    fun `re-tracks dependencies on each run`() {
        val toggle = observable(true)
        val a = observable("A")
        val b = observable("B")
        val log = mutableListOf<String>()

        reaction(supply = { if (toggle.value) a.value else b.value }) { log.add(it) }

        // Changing b should not trigger (not tracked while toggle=true)
        b.value = "B2"
        assertEquals(emptyList(), log)

        // Flip toggle — now "b" is tracked, "a" is not
        toggle.value = false
        assertEquals(listOf("B2"), log)

        // Changing a should NOT trigger
        a.value = "A2"
        assertEquals(listOf("B2"), log)

        // Changing b SHOULD trigger
        b.value = "B3"
        assertEquals(listOf("B2", "B3"), log)
    }

    @Test
    fun `name is reflected in toString`() {
        val r = reaction("myReaction", supply = { 0 }) {}
        assertEquals("Reaction(myReaction)", r.toString())
    }

    @Test
    fun `anonymous reaction toString`() {
        val r = reaction(supply = { 0 }) {}
        assertEquals("Reaction(anonymous)", r.toString())
    }
}

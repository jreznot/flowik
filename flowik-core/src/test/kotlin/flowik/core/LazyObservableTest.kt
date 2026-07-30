package flowik.core

import flowik.core.testing.LogRecorder
import org.slf4j.event.Level
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * mobx-utils' `lazyObservable` semantics for the push-from-anywhere flavour:
 * nothing runs until somebody reads, and every push afterwards notifies.
 */
class LazyObservableTest {

    @BeforeTest
    fun clearLog() = LogRecorder.clear()

    // Laziness

    @Test
    fun `the computation does not run until the value is read`() {
        var invocations = 0
        val data = lazyObservable("initial") { sink ->
            invocations++
            sink("loaded")
        }

        assertEquals(0, invocations)

        assertEquals("loaded", data.current())
        assertEquals(1, invocations)

        // Reading again does not re-run it
        assertEquals("loaded", data.current())
        assertEquals(1, invocations)
    }

    @Test
    fun `reads before the first push return the initial value`() {
        val data = lazyObservable("initial") { /* pushes later, or never */ }

        assertEquals("initial", data.current())
    }

    @Test
    fun `subscribing does not start the computation`() {
        var invocations = 0
        val data = lazyObservable(0) { invocations++ }

        data.subscribe { }
        assertEquals(0, invocations)

        data.current()
        assertEquals(1, invocations)
    }

    // Pushing

    @Test
    fun `every push notifies dependents`() {
        lateinit var push: Sink<Int>
        val data = lazyObservable(0) { sink -> push = sink }
        val seen = mutableListOf<Int>()

        autoRun { seen += data.current() }
        assertEquals(listOf(0), seen)

        push(1)
        push(2)
        assertEquals(listOf(0, 1, 2), seen)
    }

    @Test
    fun `a computed derives from a lazy observable`() {
        lateinit var push: Sink<List<String>>
        val data = lazyObservable(emptyList<String>()) { sink -> push = sink }
        val count = computed { data.current().size }

        assertEquals(0, count.value)

        push(listOf("a", "b"))
        assertEquals(2, count.value)
    }

    @Test
    fun `a synchronous push during a reaction does not re-run that reaction`() {
        val data = lazyObservable("initial") { sink -> sink("loaded") }
        var runs = 0
        val seen = mutableListOf<String>()

        autoRun {
            runs++
            seen += data.current()
        }

        // The push landed before the autoRun became a dependent, so it sees the
        // fresh value on its first and only run.
        assertEquals(1, runs)
        assertEquals(listOf("loaded"), seen)
    }

    @Test
    fun `observables read by the computation are not dependencies of the reader`() {
        val query = observable("a")
        var invocations = 0
        val data = lazyObservable("") { sink ->
            invocations++
            sink(query.value.uppercase())
        }
        var runs = 0

        autoRun {
            runs++
            data.current()
        }
        assertEquals(1, runs)

        query.value = "b"
        assertEquals(1, runs)
        assertEquals(1, invocations)
        assertEquals("A", data.current())
    }

    @Test
    fun `works as a read-only property delegate`() {
        class Store {
            val greeting by lazyObservable("…") { sink -> sink("hello") }
        }

        assertEquals("hello", Store().greeting)
    }

    // pending

    @Test
    fun `pending is set while the computation has not pushed yet`() {
        lateinit var push: Sink<Int>
        val data = lazyObservable(0) { sink -> push = sink }

        assertFalse(data.pending.value, "not started yet")

        data.current()
        assertTrue(data.pending.value)

        push(1)
        assertFalse(data.pending.value)
    }

    @Test
    fun `pending is reactive`() {
        lateinit var push: Sink<Int>
        val data = lazyObservable(0) { sink -> push = sink }
        val seen = mutableListOf<Boolean>()

        autoRun { seen += data.pending.value }
        assertEquals(listOf(false), seen)

        data.current()
        push(1)
        assertEquals(listOf(false, true, false), seen)
    }

    // refresh / reset

    @Test
    fun `refresh does nothing before the first read`() {
        var invocations = 0
        val data = lazyObservable(0) { invocations++ }

        data.refresh()
        assertEquals(0, invocations)

        data.current()
        assertEquals(1, invocations)
    }

    @Test
    fun `refresh re-invokes the computation`() {
        var invocations = 0
        val data = lazyObservable(0) { sink ->
            sink(++invocations)
        }

        assertEquals(1, data.current())

        data.refresh()
        assertEquals(2, data.current())
        assertEquals(2, invocations)
    }

    @Test
    fun `a push from a superseded run is dropped`() {
        val sinks = mutableListOf<Sink<Int>>()
        val data = lazyObservable(0) { sink -> sinks += sink }

        data.current()
        data.refresh()
        assertEquals(2, sinks.size)

        sinks[0](5)                       // stale run, ignored
        assertEquals(0, data.current())

        sinks[1](7)
        assertEquals(7, data.current())
    }

    @Test
    fun `reset restores the initial value and unstarts the computation`() {
        var invocations = 0
        val data = lazyObservable("initial") { sink ->
            invocations++
            sink("loaded")
        }
        assertEquals("loaded", data.current())

        data.reset()
        assertEquals(1, invocations, "reset alone does not recompute")

        // …but the next read does
        assertEquals("loaded", data.current())
        assertEquals(2, invocations)
    }

    @Test
    fun `reset notifies dependents`() {
        lateinit var push: Sink<String>
        val data = lazyObservable("initial") { sink -> push = sink }
        val seen = mutableListOf<String>()

        autoRun { seen += data.current() }
        push("loaded")
        assertEquals(listOf("initial", "loaded"), seen)

        data.reset()
        // The autoRun re-reads, which starts the computation again — it pushes
        // nothing this time, so the value stays at the initial one.
        assertEquals(listOf("initial", "loaded", "initial"), seen)
    }

    // dispose

    @Test
    fun `a disposed lazy observable keeps its value and ignores later pushes`() {
        val sinks = mutableListOf<Sink<Int>>()
        val data = lazyObservable(0) { sink -> sinks += sink }

        data.current()
        sinks[0](1)
        assertEquals(1, data.current())

        data.dispose()
        sinks[0](2)

        assertEquals(1, data.current())
        assertEquals(1, sinks.size, "a read after dispose does not restart it")
        assertFalse(data.pending.value)
    }

    @Test
    fun `dispose can be registered with Bindings`() {
        val data = lazyObservable(0) { sink -> sink(1) }
        val bindings = Bindings()
        bindings.register(data)

        assertEquals(1, data.current())
        bindings.dispose()

        data.refresh()
        assertEquals(1, data.current())
    }

    // Errors

    @Test
    fun `a failing computation reports to onError and stops pending`() {
        val errors = mutableListOf<Throwable>()
        val data = lazyObservable("initial", onError = { errors += it }) { error("boom") }

        assertEquals("initial", data.current())
        assertEquals(listOf("boom"), errors.map { it.message })
        assertFalse(data.pending.value)
    }

    @Test
    fun `a failing computation is logged when no handler is given`() {
        val data = lazyObservable("initial") { error("boom") }

        data.current()

        val event = LogRecorder.events.single()
        assertEquals(Level.ERROR, event.level)
        assertEquals("boom", event.error?.message)
    }

    @Test
    fun `a failure does not prevent a later refresh from succeeding`() {
        var attempts = 0
        val data = lazyObservable("initial", onError = { }) { sink ->
            if (++attempts == 1) error("boom")
            sink("loaded")
        }

        assertEquals("initial", data.current())

        data.refresh()
        assertEquals("loaded", data.current())
    }

    // Bindings see it as an ordinary readable observable

    @Test
    fun `is a ReadableObservable, so one-way bindings accept it`() {
        val data: ReadableObservable<String> = lazyObservable("initial") { sink -> sink("loaded") }

        assertEquals("loaded", data.get())
        assertSame(data.value, data.get())
    }
}

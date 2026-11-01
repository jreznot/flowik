package flowik.core

import flowik.core.testing.LogRecorder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlobalErrorHandlerTest {

    private val installed = mutableListOf<Disposable>()

    @BeforeTest
    fun clearLog() = LogRecorder.clear()

    @AfterTest
    fun uninstall() = installed.forEach { it.dispose() }

    private fun install(handler: (Throwable, Any) -> Unit) {
        installed += onReactionError(handler)
    }

    @Test
    fun `receives what a reaction without onError throws, in place of the log`() {
        val reported = mutableListOf<Pair<String?, Any>>()
        install { error, source -> reported += error.message to source }

        val r = autoRun(name = "failing") { error("boom") }

        assertEquals(listOf<Pair<String?, Any>>("boom" to r), reported)
        assertTrue(LogRecorder.events.isEmpty())
    }

    @Test
    fun `a reaction's own onError takes precedence`() {
        val global = mutableListOf<Throwable>()
        val local = mutableListOf<Throwable>()
        install { error, _ -> global += error }

        reaction(supply = { error("boom") }, onError = { local += it }) {}

        assertEquals(1, local.size)
        assertTrue(global.isEmpty())
    }

    @Test
    fun `every handler runs, even after one of them throws`() {
        val seen = mutableListOf<String>()
        install { _, _ -> seen += "first"; error("handler failed") }
        install { _, _ -> seen += "second" }

        whenThen(check = { error("boom") }) {}

        assertEquals(listOf("first", "second"), seen)
        val logged = LogRecorder.events.single()
        assertEquals("handler failed", logged.error?.message)
        assertEquals("boom", logged.error?.suppressed?.single()?.message)
    }

    @Test
    fun `also receives failures of lazy observables and observation hooks`() {
        val sources = mutableListOf<Any>()
        install { _, source -> sources += source }

        val lazy = lazyObservable(0) { error("load failed") }
        val atom = observable(0)
        atom.onBecomeObserved { error("hook failed") }
        autoRun { lazy.value; atom.value }

        assertEquals(2, sources.size)
        assertSame(lazy, sources[0])
    }

    @Test
    fun `logging resumes once the handler is disposed`() {
        val handler = onReactionError { _, _ -> }
        handler.dispose()

        autoRun { error("boom") }

        assertEquals(1, LogRecorder.events.size)
    }
}

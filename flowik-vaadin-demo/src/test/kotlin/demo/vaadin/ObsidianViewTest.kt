package demo.vaadin

import com.vaadin.flow.component.ClickEvent
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.dom.DomEvent
import com.vaadin.flow.internal.JacksonUtils
import com.vaadin.flow.internal.nodefeature.ElementListenerMap
import demo.vaadin.obsidian.*
import flowik.core.action
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObsidianViewTest {

    private fun Component.descendants(): List<Component> =
        children.toList().flatMap { listOf(it) + it.descendants() }

    private fun Component.hasClass(name: String) = element.classList.contains(name)

    /** Visible itself and inside nothing hidden — what the user can actually see. */
    private fun Component.isShown(): Boolean = isVisible && parent.map { it.isShown() }.orElse(true)

    private fun Component.rows() = descendants().filterIsInstance<NoteRow>().filter { it.isShown() }

    private fun Component.tabs() = descendants().filterIsInstance<NoteTab>()

    private fun Component.editorTitle() =
        descendants().single { it.hasClass("obs-inline-title") }.element.text

    private fun Component.click() = ComponentUtil.fireEvent(this, ClickEvent(this))

    /** A DOM event as the browser sends it — `NoteTab` listens on the element, not for a `ClickEvent`. */
    private fun Component.fireDomEvent(type: String) {
        val listeners = element.node.getFeature(ElementListenerMap::class.java)
        // The browser evaluates each listener's filter and sends the result
        // along; the server only runs the listeners whose filter passed.
        val data = JacksonUtils.createObjectNode()
        listeners.getExpressions(type).forEach { data.put(it, true) }
        listeners.fireEvent(DomEvent(element, type, data))
    }

    @Test
    fun `the vault starts with one note open`() {
        val store = NotesStore()
        val view = ObsidianView(store)

        assertEquals(5, view.rows().size)
        assertEquals(listOf("Welcome.md"), view.tabs().map { it.note[Note::name] })
        assertEquals("Welcome", view.editorTitle())
        assertTrue(view.rows().first().hasClass("active"))
    }

    @Test
    fun `opening a note adds a tab and moves the editor and the list highlight`() {
        val store = NotesStore()
        val view = ObsidianView(store)

        view.rows()[2].click()
        assertEquals(2, view.tabs().size)
        assertEquals("Meeting notes 2026-08-22", view.editorTitle())
        assertTrue(view.rows()[2].hasClass("active"))
        assertFalse(view.rows()[0].hasClass("active"))

        // Back to the first tab: no new tab, the editor follows.
        view.tabs().first().fireDomEvent("click")
        assertEquals(2, view.tabs().size)
        assertEquals("Welcome", view.editorTitle())
    }

    @Test
    fun `the filter only applies while it is showing`() {
        val store = NotesStore()
        val view = ObsidianView(store)

        action { store.filterText = "notes" }
        assertEquals(5, view.rows().size)

        store.showFilter()
        assertEquals(1, view.rows().size)
        assertEquals("1 of 5 notes", store.countText)

        action { store.filterVisible = false }
        assertEquals(5, view.rows().size)
        assertEquals("notes", store.filterText)
    }

    @Test
    fun `a tab is closed only once it has collapsed`() {
        val store = NotesStore()
        val view = ObsidianView(store)
        val welcome = store.activeNote!!
        view.rows()[1].click()
        view.tabs().first().fireDomEvent("click")

        val tab = view.tabs().first()
        tab.descendants().single { it.hasClass("obs-tab-close") }.click()
        assertTrue(tab.hasClass("closing"))
        assertEquals(2, store.openNotes.size)
        assertSame(welcome, store.activeNote)

        tab.fireDomEvent("animationend")
        assertEquals(listOf("Flowik cheat sheet.md"), view.tabs().map { it.note[Note::name] })
        assertEquals("Flowik cheat sheet", view.editorTitle())
    }

    @Test
    fun `selecting a collapsing tab calls off the close`() {
        val store = NotesStore()
        val view = ObsidianView(store)
        view.rows()[1].click()

        val welcomeTab = view.tabs().first()
        welcomeTab.descendants().single { it.hasClass("obs-tab-close") }.click()
        assertTrue(welcomeTab.hasClass("closing"))

        view.rows()[0].click()
        assertFalse(welcomeTab.hasClass("closing"))
        welcomeTab.fireDomEvent("animationend")
        assertEquals(2, store.openNotes.size)
    }

    @Test
    fun `closing the last tab shows the empty state`() {
        val store = NotesStore()
        val view = ObsidianView(store)

        action { store.closeNote(store.activeNote!!) }
        assertNull(store.activeNote)
        assertTrue(view.tabs().isEmpty())
        assertTrue(view.descendants().any { it.hasClass("obs-empty-state") })

        store.createUntitledNote()
        store.createUntitledNote()
        assertEquals(listOf("Untitled.md", "Untitled 1.md"), view.tabs().map { it.note[Note::name] })
        assertEquals("Untitled 1", view.editorTitle())
    }

    @Test
    fun `the sidebars and the rail follow the store`() {
        val store = NotesStore()
        val view = ObsidianView(store)
        val slides = view.descendants().filter { it.hasClass("obs-slide") }

        action { store.leftVisible = false }
        assertTrue(slides.first().hasClass("collapsed"))
        assertFalse(slides.last().hasClass("collapsed"))

        action { store.activeTool = "Tags" }
        assertTrue(view.rows().isEmpty())
        assertTrue(view.descendants().any { it.element.text == "Tags is not part of this demo" && it.isShown() })
    }
}

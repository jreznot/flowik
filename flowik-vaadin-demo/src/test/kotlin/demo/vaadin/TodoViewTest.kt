package demo.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The view is built without a UI session, which is enough to exercise the
 * bindings: they are plain reactions over the store, not client round-trips.
 */
class TodoViewTest {

    private fun Component.descendants(): List<Component> =
        children.toList().flatMap { listOf(it) + it.descendants() }

    @Test
    fun `the view binds while it is alive and stops when it is detached`() {
        val view = TodoView()

        // Three items were added in init, one row each. TodoRow is file-private,
        // so the test recognises it by name rather than by type.
        val rows = view.descendants().filter { it.javaClass.simpleName == "TodoRow" }
        assertEquals(3, rows.size)

        val status = view.descendants().filterIsInstance<Span>().map { it.text }
        assertTrue(status.contains("0 / 3 completed"), "status text missing in $status")

        val toggle = view.descendants().filterIsInstance<Button>().single { "filter" in it.text }
        val filterPanel = view.descendants()
            .filterIsInstance<HorizontalLayout>()
            .single { panel -> panel.children.anyMatch { it is Checkbox && it.label == "Show completed" } }

        assertEquals("Show filter", toggle.text)
        assertFalse(filterPanel.isVisible)

        // A server-side click runs the same handler the browser would.
        toggle.click()
        assertEquals("Hide filter", toggle.text)
        assertTrue(filterPanel.isVisible)

        // Detaching the routed view releases its group — the store still
        // changes, nothing follows it any more.
        ComponentUtil.onComponentDetach(view)
        toggle.click()
        assertEquals("Hide filter", toggle.text)
        assertTrue(filterPanel.isVisible)
    }
}

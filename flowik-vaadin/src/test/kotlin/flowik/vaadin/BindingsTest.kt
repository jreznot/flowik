package flowik.vaadin

import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableList
import flowik.core.action
import flowik.core.observable
import kotlin.test.Test
import kotlin.test.assertEquals

class BindingsTest {

    /** The shape the demo uses: the row is its own group. */
    private class Row(text: MutableObservable<String>) : HorizontalLayout(), Bindings by Bindings() {
        val caption = Span().apply {
            // Both of these have to register with the row — the second one in
            // particular, since `flowik.core.autoRun` would also be a candidate
            // here and it would never be disposed.
            text { text.value }
            autoRun("row.style") { style.set("font-weight", if (text.value.isEmpty()) "normal" else "bold") }
        }

        init {
            add(caption)
        }
    }

    @Test
    fun `bindings live until the group is disposed`() {
        val text = observable("first")
        val row = Row(text)

        assertEquals("first", row.caption.text)
        assertEquals("bold", row.caption.style.get("font-weight"))

        action { text.value = "second" }
        assertEquals("second", row.caption.text)

        row.dispose()
        action { text.value = "" }
        assertEquals("second", row.caption.text)
        assertEquals("bold", row.caption.style.get("font-weight"))
    }

    @Test
    fun `registering a child releases it with its owner`() {
        val text = observable("child")
        val owner = Bindings()
        val row = owner.register(Row(text))

        action { text.value = "updated" }
        assertEquals("updated", row.caption.text)

        owner.dispose()
        action { text.value = "ignored" }
        assertEquals("updated", row.caption.text)
    }

    @Test
    fun `a row dropped by items is disposed with the item it showed`() {
        val text = observable("row")
        val items = ObservableList<String>()
        items.add("only")

        val bindings = Bindings()
        val rows = mutableListOf<Row>()
        val list = VerticalLayout()
        context(bindings) {
            list.items(items) { Row(text).also { rows.add(it) } }
        }

        val row = rows.single()
        action { text.value = "visible" }
        assertEquals("visible", row.caption.text)

        action { items.removeAt(0) }
        action { text.value = "gone" }
        assertEquals("visible", row.caption.text)
        assertEquals(0, list.componentCount)
    }

    @Test
    fun `disposeOnDetach releases the group when the component is detached`() {
        val text = observable("attached")
        val row = Row(text).also { it.disposeOnDetach() }

        action { text.value = "still here" }
        assertEquals("still here", row.caption.text)

        // What Vaadin itself calls when a component leaves an attached tree;
        // a plain unit test has no UI to attach to.
        ComponentUtil.onComponentDetach(row)
        action { text.value = "detached" }
        assertEquals("still here", row.caption.text)
    }
}

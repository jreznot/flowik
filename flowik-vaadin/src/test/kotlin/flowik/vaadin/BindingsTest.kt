package flowik.vaadin

import com.vaadin.flow.component.AbstractField
import com.vaadin.flow.component.ClickEvent
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.component.avatar.Avatar
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.tabs.Tabs
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.ObservableList
import flowik.core.action
import flowik.core.observable
import flowik.core.observableRef
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
    fun `childSwitch swaps the child and disposes the one it drops`() {
        val selected = observable("a")
        val caption = observable("first")
        val built = mutableListOf<Row>()

        val pane = Div()
        context(Bindings()) {
            pane.childSwitch(selected) {
                // A read while building belongs to the child, not to the switch.
                caption.value
                Row(caption).also { built.add(it) }
            }
        }

        action { caption.value = "second" }
        assertEquals(1, built.size)
        assertEquals("second", built[0].caption.text)

        action { selected.value = "b" }
        assertEquals(2, built.size)
        assertEquals(listOf<Any>(built[1]), pane.children.toList())

        action { caption.value = "third" }
        assertEquals("second", built[0].caption.text)
        assertEquals("third", built[1].caption.text)
    }

    @Test
    fun `items does not rebuild when a row reads an item while it is built`() {
        val names = observable("x")
        val list = ObservableList(listOf("one", "two"))
        var builds = 0

        val container = VerticalLayout()
        context(Bindings()) {
            container.items({ list.items }) { item ->
                builds++
                Span("$item ${names.value}")
            }
        }
        assertEquals(2, builds)

        action { names.value = "y" }
        assertEquals(2, builds)

        action { list.add("three") }
        assertEquals(5, builds)
    }

    @Test
    fun `tabs follow the selection and write it back`() {
        val section = observableRef("job")
        val tabs = Tabs()
        context(Bindings()) {
            tabs.items(listOf("personal", "job", "documents"), section) { it.uppercase() }
        }
        assertEquals(1, tabs.selectedIndex)
        assertEquals("JOB", tabs.selectedTab.label)

        action { section.value = "documents" }
        assertEquals(2, tabs.selectedIndex)

        tabs.selectedIndex = 0
        assertEquals("personal", section.value)
    }

    @Test
    fun `a toggled class leaves the other classes alone`() {
        val selected = observable(false)
        val row = Div().apply { addClassName("row") }
        context(Bindings()) {
            row.className("selected") { selected.value }
            row.attribute("aria-current") { if (selected.value) "true" else null }
        }
        assertEquals(setOf("row"), row.classNames.toSet())
        assertEquals(null, row.element.getAttribute("aria-current"))

        action { selected.value = true }
        assertEquals(setOf("row", "selected"), row.classNames.toSet())
        assertEquals("true", row.element.getAttribute("aria-current"))
    }

    @Test
    fun `avatar shows the abbreviation it is bound to`() {
        val name = observable("Cody Fisher")
        val avatar = Avatar()
        context(Bindings()) {
            avatar.name { name.value }
            avatar.abbreviation { name.value.take(1) }
        }
        assertEquals("C", avatar.abbreviation)

        action { name.value = "Esther Howard" }
        assertEquals("Esther Howard", avatar.name)
        assertEquals("E", avatar.abbreviation)
    }

    @Test
    fun `a generic field is bound both ways`() {
        val notes = observable("first")
        val area = TextArea()
        context(Bindings()) {
            area.value(notes)
        }
        assertEquals("first", area.value)

        action { notes.value = "second" }
        assertEquals("second", area.value)

        // A server-side change is not written back…
        area.value = "typed"
        assertEquals("second", notes.value)

        // …a change from the client is.
        ComponentUtil.fireEvent(area, AbstractField.ComponentValueChangeEvent(area, area, "second", true))
        assertEquals("typed", notes.value)
    }

    @Test
    fun `a controlled field shows its state, not what was typed`() {
        val query = observable("")
        val field = TextField()
        context(Bindings()) {
            // The owner keeps only upper case, and nothing longer than 3.
            field.value(query) { typed -> if (typed.length <= 3) query.value = typed.uppercase() }
        }

        field.value = "ab"
        ComponentUtil.fireEvent(field, AbstractField.ComponentValueChangeEvent(field, field, "", true))
        assertEquals("AB", query.value)
        assertEquals("AB", field.value)

        field.value = "abcd"
        ComponentUtil.fireEvent(field, AbstractField.ComponentValueChangeEvent(field, field, "AB", true))
        assertEquals("AB", query.value)
        assertEquals("AB", field.value)
    }

    @Test
    fun `controlled tabs report a click and show what the owner decided`() {
        val selected = observableRef("personal")
        val clicks = mutableListOf<String>()
        val tabs = Tabs()
        context(Bindings()) {
            // The owner ignores clicks on "documents".
            tabs.items(listOf("personal", "job", "documents"), selected, onSelect = { option ->
                clicks += option
                if (option != "documents") selected.value = option
            })
        }

        tabs.selectedIndex = 1
        assertEquals("job", selected.value)

        tabs.selectedIndex = 2
        assertEquals(listOf("job", "documents"), clicks)
        assertEquals("job", selected.value)
        assertEquals(1, tabs.selectedIndex)
    }

    @Test
    fun `onClick runs the handler as an action`() {
        val a = observable(0)
        val b = observable(0)
        val seen = mutableListOf<Pair<Int, Int>>()
        Bindings().autoRun { seen.add(a.value to b.value) }

        val card = Div()
        card.onClick {
            a.value = 1
            b.value = 1
        }
        ComponentUtil.fireEvent(card, ClickEvent(card))

        // One batched update, never the half-written (1, 0).
        assertEquals(listOf(0 to 0, 1 to 1), seen)
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

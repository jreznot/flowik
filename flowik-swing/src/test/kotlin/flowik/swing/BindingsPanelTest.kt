package flowik.swing

import flowik.core.Bindings
import flowik.core.action
import flowik.core.observable
import flowik.layout.PanelScope
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingsPanelTest {

    private class Caption(text: flowik.core.MutableObservable<String>) : BindingsPanel(BorderLayout()) {
        val label = JLabel().also {
            // The panel is the Bindings group: no argument to pass, no context block.
            it.text { text.value }
            add(it, BorderLayout.CENTER)
        }
    }

    @Test
    fun `bindings live until they are disposed, not until the component is detached`() {
        val text = observable("first")
        val caption = Caption(text)
        val parent = JPanel(BorderLayout()).apply { add(caption, BorderLayout.CENTER) }

        assertEquals("first", caption.label.text)

        // The old machinery released the reactions here — a card switch or a tab
        // reorder was enough to break the binding.
        parent.remove(caption)
        action { text.value = "second" }
        assertEquals("second", caption.label.text)

        caption.dispose()
        action { text.value = "third" }
        assertEquals("second", caption.label.text)
    }

    @Test
    fun `registering a child panel disposes it with its owner`() {
        val text = observable("child")
        val owner = BindingsPanel(BorderLayout())
        val child = owner.register(Caption(text))
        owner.add(child, BorderLayout.CENTER)

        action { text.value = "updated" }
        assertEquals("updated", child.label.text)

        owner.dispose()
        action { text.value = "ignored" }
        assertEquals("updated", child.label.text)
    }

    @Test
    fun `a panel scope hands its group to the builders inside it`() {
        val bindings = Bindings()
        val flag = observable(true)
        val root = JPanel()

        val label = PanelScope(root, bindings).run {
            panel.visible { flag.value }        // binds the panel itself
            Label { "flag = ${flag.value}" }    // and a widget inside it
        }

        assertEquals("flag = true", label.text)
        action { flag.value = false }
        assertEquals("flag = false", label.text)
        assertFalse(root.isVisible)

        bindings.dispose()
        action { flag.value = true }
        assertEquals("flag = false", label.text)
        assertFalse(root.isVisible)
    }

    @Test
    fun `a dropped ForEach child is disposed with the row it built`() {
        val text = observable("row")
        val items = flowik.core.ObservableList<String>()
        items.add("only")

        val bindings = Bindings()
        val captions = mutableListOf<Caption>()
        val container = JPanel()
        context(bindings) {
            container.items(items) { Caption(text).also { captions.add(it) } }
        }

        val caption = captions.single()
        action { text.value = "visible" }
        assertEquals("visible", caption.label.text)

        action { items.removeAt(0) }
        action { text.value = "gone" }
        assertEquals("visible", caption.label.text)
        assertTrue(container.componentCount == 0)
    }
}

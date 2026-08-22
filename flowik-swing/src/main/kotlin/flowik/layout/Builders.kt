package flowik.layout

import flowik.core.Bindings
import java.awt.*
import javax.swing.*

@DslMarker
annotation class ReaktorDsl

/**
 * The builder receiver: the container being filled, plus the [Bindings] group
 * every binding created inside it registers with.
 *
 * The scope *is* the group — it implements [Bindings] by delegating to the one
 * handed to [uiFrame] — which is what lets `Label { … }`, `TextField(store::name)`
 * or a bare `autoRun { }` inside the builder find it with nothing threaded
 * through. All the nested scopes share the frame's single group, so
 * `dispose()`ing one disposes the whole frame's bindings.
 */
@ReaktorDsl
open class PanelScope(val panel: JPanel, bindings: Bindings) : Bindings by bindings {

    fun add(component: JComponent, constraints: Any? = null): JComponent {
        if (constraints != null) panel.add(component, constraints) else panel.add(component)
        return component
    }

    fun hbox(gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = object : JPanel() {
            override fun getMaximumSize(): Dimension {
                val pref = preferredSize
                return Dimension(Int.MAX_VALUE, pref.height)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(gap, gap, gap, gap)
        }
        PanelScope(child, this).init()
        panel.add(child)
        return child
    }

    fun vbox(gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(gap, gap, gap, gap)
        }
        PanelScope(child, this).init()
        panel.add(child)
        return child
    }

    fun grid(cols: Int, gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel(GridLayout(0, cols, gap, gap))
        PanelScope(child, this).init()
        panel.add(child)
        return child
    }

    fun Panel(layout: LayoutManager? = null, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel(layout ?: FlowLayout())
        PanelScope(child, this).init()
        panel.add(child)
        return child
    }

    fun borderPanel(gap: Int = 5, init: BorderPanelScope.() -> Unit): JPanel {
        val child = JPanel(BorderLayout(gap, gap))
        BorderPanelScope(child, this).init()
        panel.add(child)
        return child
    }

    /** Add vertical glue (for BoxLayout). */
    fun vglue() {
        panel.add(Box.createVerticalGlue())
    }

    /** Add horizontal glue (for BoxLayout). */
    fun hglue() {
        panel.add(Box.createHorizontalGlue())
    }

    /** Add a rigid spacing area. */
    fun spacer(width: Int = 0, height: Int = 0) {
        panel.add(Box.createRigidArea(Dimension(width, height)))
    }

    /** Add a horizontal separator line. */
    fun separator() {
        val sep = JSeparator()
        sep.maximumSize = Dimension(Int.MAX_VALUE, sep.preferredSize.height)
        panel.add(sep)
    }
}

@ReaktorDsl
class BorderPanelScope(panel: JPanel, bindings: Bindings) : PanelScope(panel, bindings) {
    fun north(init: PanelScope.() -> Unit) = region(BorderLayout.NORTH, init)
    fun south(init: PanelScope.() -> Unit) = region(BorderLayout.SOUTH, init)
    fun east(init: PanelScope.() -> Unit) = region(BorderLayout.EAST, init)
    fun west(init: PanelScope.() -> Unit) = region(BorderLayout.WEST, init)
    fun center(init: PanelScope.() -> Unit) = region(BorderLayout.CENTER, init)

    private fun region(constraint: String, init: PanelScope.() -> Unit) {
        val regionPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        PanelScope(regionPanel, this).init()
        panel.add(regionPanel, constraint)
    }
}

/**
 * Builds a window and fills it, with [bindings] owning every reaction the UI
 * inside creates.
 *
 * The group is a parameter rather than something the frame invents, because
 * releasing it is a decision: an application frame can leave it alone and let
 * the process end, while a window that is opened and closed repeatedly should
 * pass its own and hand it to `disposeOnClose`:
 *
 * ```kotlin
 * val bindings = Bindings()
 * uiFrame("Report", bindings = bindings, exitOnClose = false) { … }
 *     .disposeOnClose(bindings)
 * ```
 */
fun uiFrame(
    title: String,
    width: Int = 600,
    height: Int = 400,
    exitOnClose: Boolean = true,
    bindings: Bindings,
    init: BorderPanelScope.() -> Unit
): JFrame {
    return JFrame(title).apply {
        defaultCloseOperation =
            if (exitOnClose) JFrame.EXIT_ON_CLOSE else JFrame.DISPOSE_ON_CLOSE
        val root = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        BorderPanelScope(root, bindings).init()
        contentPane = root
        setSize(width, height)
        setLocationRelativeTo(null)
        isVisible = true
    }
}

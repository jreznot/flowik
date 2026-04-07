package reaktor.layout

import java.awt.*
import javax.swing.*

@DslMarker
annotation class ReaktorDsl

@ReaktorDsl
open class PanelScope(val panel: JPanel) {

    fun add(component: JComponent, constraints: Any? = null): JComponent {
        if (constraints != null) panel.add(component, constraints) else panel.add(component)
        return component
    }

    fun rhbox(gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = object : JPanel() {
            override fun getMaximumSize(): Dimension {
                val pref = preferredSize
                return Dimension(Int.MAX_VALUE, pref.height)
            }
        }.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(gap, gap, gap, gap)
        }
        PanelScope(child).init()
        panel.add(child)
        return child
    }

    fun rvbox(gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(gap, gap, gap, gap)
        }
        PanelScope(child).init()
        panel.add(child)
        return child
    }

    fun rgrid(cols: Int, gap: Int = 5, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel(GridLayout(0, cols, gap, gap))
        PanelScope(child).init()
        panel.add(child)
        return child
    }

    fun rpanel(layout: LayoutManager? = null, init: PanelScope.() -> Unit): JPanel {
        val child = JPanel(layout ?: FlowLayout())
        PanelScope(child).init()
        panel.add(child)
        return child
    }

    fun rborderPanel(gap: Int = 5, init: BorderPanelScope.() -> Unit): JPanel {
        val child = JPanel(BorderLayout(gap, gap))
        BorderPanelScope(child).init()
        panel.add(child)
        return child
    }

    /** Add vertical glue (for BoxLayout). */
    fun vglue() { panel.add(Box.createVerticalGlue()) }

    /** Add horizontal glue (for BoxLayout). */
    fun hglue() { panel.add(Box.createHorizontalGlue()) }

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
class BorderPanelScope(panel: JPanel) : PanelScope(panel) {

    fun north(init: PanelScope.() -> Unit) = region(BorderLayout.NORTH, init)
    fun south(init: PanelScope.() -> Unit) = region(BorderLayout.SOUTH, init)
    fun east(init: PanelScope.() -> Unit) = region(BorderLayout.EAST, init)
    fun west(init: PanelScope.() -> Unit) = region(BorderLayout.WEST, init)
    fun center(init: PanelScope.() -> Unit) = region(BorderLayout.CENTER, init)

    private fun region(constraint: String, init: PanelScope.() -> Unit) {
        val regionPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }
        PanelScope(regionPanel).init()
        panel.add(regionPanel, constraint)
    }
}

fun rframe(
    title: String,
    width: Int = 600,
    height: Int = 400,
    exitOnClose: Boolean = true,
    init: BorderPanelScope.() -> Unit
): JFrame {
    return JFrame(title).apply {
        defaultCloseOperation =
            if (exitOnClose) JFrame.EXIT_ON_CLOSE else JFrame.DISPOSE_ON_CLOSE
        val root = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        BorderPanelScope(root).init()
        contentPane = root
        setSize(width, height)
        setLocationRelativeTo(null)
        isVisible = true
    }
}

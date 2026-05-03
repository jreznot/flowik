package flowik.swing

import flowik.layout.PanelScope
import java.awt.LayoutManager
import java.util.function.Supplier
import javax.swing.BoxLayout
import javax.swing.JPanel

fun PanelScope.Panel(
    visible: Supplier<Boolean>,
    layout: LayoutManager? = null,
    init: PanelScope.() -> Unit
): JPanel {
    val child = JPanel()
    child.layout = layout ?: BoxLayout(child, BoxLayout.Y_AXIS)
    child.visible(visible)
    PanelScope(child).init()
    panel.add(child)
    return child
}

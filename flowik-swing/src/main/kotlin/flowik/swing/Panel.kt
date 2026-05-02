package flowik.swing

import flowik.core.ObservableValue
import flowik.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JPanel

fun JPanel.bindVisible(visible: ObservableValue<Boolean>) {
    autoRun("JPanel.visibility") {
        val shouldBeVisible = visible.value
        if (isVisible != shouldBeVisible) {
            isVisible = shouldBeVisible
            parent?.revalidate()
            parent?.repaint()
        }
    }
}

fun PanelScope.Panel(
    visible: ObservableValue<Boolean>,
    layout: LayoutManager? = null,
    init: PanelScope.() -> Unit
): JPanel {
    val child = JPanel()
    child.layout = layout ?: BoxLayout(child, BoxLayout.Y_AXIS)
    child.bindVisible(visible)
    PanelScope(child).init()
    panel.add(child)
    return child
}

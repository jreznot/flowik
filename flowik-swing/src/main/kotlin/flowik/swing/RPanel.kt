package flowik.swing

import flowik.core.ObservableValue
import flowik.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A reactive panel whose visibility can be bound to an [ObservableValue]
 * via [bindVisible].
 */
class RPanel(layout: LayoutManager? = null) : JPanel(), RComponent {

    init {
        this.layout = layout ?: BoxLayout(this, BoxLayout.Y_AXIS)
    }

    fun bindVisible(visible: ObservableValue<Boolean>) {
        autoReaction("RPanel.visibility") {
            val shouldBeVisible = visible.value
            if (isVisible != shouldBeVisible) {
                isVisible = shouldBeVisible
                parent?.revalidate()
                parent?.repaint()
            }
        }
    }

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

fun PanelScope.rpanel(
    visible: ObservableValue<Boolean>,
    layout: LayoutManager? = null,
    init: PanelScope.() -> Unit
): RPanel {
    val child = RPanel(layout)
    child.bindVisible(visible)
    PanelScope(child).init()
    panel.add(child)
    return child
}

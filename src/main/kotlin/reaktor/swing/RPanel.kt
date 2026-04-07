package reaktor.swing

import reaktor.core.ObservableValue
import reaktor.layout.PanelScope
import java.awt.LayoutManager
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A reactive panel whose visibility can be bound to an [ObservableValue].
 *
 * When the observed boolean changes, the panel automatically shows or hides
 * itself and asks its parent to re-layout.
 */
class RPanel(
    layout: LayoutManager? = null,
    private val visible: ObservableValue<Boolean>? = null
) : JPanel(), RComponent {

    init {
        this.layout = layout ?: BoxLayout(this, BoxLayout.Y_AXIS)

        visible?.let { vis ->
            autoReaction("RPanel.visibility") {
                val shouldBeVisible = vis.value
                if (isVisible != shouldBeVisible) {
                    isVisible = shouldBeVisible
                    parent?.revalidate()
                    parent?.repaint()
                }
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
    val child = RPanel(layout, visible)
    PanelScope(child).init()
    panel.add(child)
    return child
}

package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.core.toggle
import flowik.swing.autoRun
import flowik.swing.text
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.function.Supplier
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.border.MatteBorder

/**
 * The window toolbar: decorative icon clusters on both sides, the current
 * document name in the middle, and the two sidebar toggles.
 *
 * @param title           text shown in the centre
 * @param leftPanelVisible  toggled by the left-hand switch, which is drawn
 *                          active while the panel is open
 * @param rightPanelVisible same, for the right-hand switch
 * @param leftIcons       decorative icons before the left switch
 * @param rightIcons      decorative icons before the right switch
 */
class TopBar(
    title: Supplier<String>,
    leftPanelVisible: MutableObservable<Boolean>,
    rightPanelVisible: MutableObservable<Boolean>,
    leftIcons: List<ToolIcon> = emptyList(),
    rightIcons: List<ToolIcon> = emptyList()
) : JPanel(BorderLayout()) {

    init {
        background = BG_SIDEBAR
        border = MatteBorder(0, 0, 1, 0, LINE)
        preferredSize = Dimension(0, 40)

        val toggleLeft = IconButton(CoreUiFree.VIEW_COLUMN, "Toggle left sidebar") { leftPanelVisible.toggle() }
        toggleLeft.autoRun("topBar.toggleLeft") { toggleLeft.active = leftPanelVisible.value }

        val toggleRight = IconButton(CoreUiFree.COLUMNS, "Toggle right sidebar") { rightPanelVisible.toggle() }
        toggleRight.autoRun("topBar.toggleRight") { toggleRight.active = rightPanelVisible.value }

        val titleLabel = caption("", size = 12f).apply {
            horizontalAlignment = SwingConstants.CENTER
            text(title)
        }

        add(toolIconRow(FlowLayout.LEFT, 6, leftIcons, verticalSeparator(), toggleLeft), BorderLayout.WEST)
        add(titleLabel, BorderLayout.CENTER)
        add(toolIconRow(FlowLayout.RIGHT, 6, rightIcons, verticalSeparator(), toggleRight), BorderLayout.EAST)
    }
}

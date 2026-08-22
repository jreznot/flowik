package demo.swing.obsidian

import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

/**
 * The right sidebar. Its mentions section is static — this demo has no link
 * graph — and it carries a single live link in the middle. Wrap it in a
 * [SlidingPanel] to make it collapsible.
 *
 * @param linkText      caption of the centred link
 * @param onLinkClicked what the link does; the panel does not care whether that
 *                      opens a browser
 */
class BacklinksPanel(
    linkText: String,
    onLinkClicked: () -> Unit,
    preferredWidth: Int = 270
) : JPanel(BorderLayout()) {

    init {
        background = BG_SIDEBAR
        border = MatteBorder(0, 1, 0, 0, LINE)
        preferredSize = Dimension(preferredWidth, 0)

        add(
            JPanel(BorderLayout()).apply {
                isOpaque = false
                add(header(), BorderLayout.NORTH)
                add(mentions(), BorderLayout.CENTER)
            },
            BorderLayout.NORTH
        )

        // GridBagLayout with a single child centres it in both directions.
        add(
            JPanel(GridBagLayout()).apply {
                isOpaque = false
                add(linkLabel(linkText, CoreUiFree.EXTERNAL_LINK, onClick = onLinkClicked))
            },
            BorderLayout.CENTER
        )
    }

    private fun header() = toolIconRow(
        FlowLayout.RIGHT, 6,
        listOf(
            ToolIcon(CoreUiFree.LIST_RICH, "Collapse results"),
            ToolIcon(CoreUiFree.SWAP_VERTICAL, "Expand results"),
            ToolIcon(CoreUiFree.SORT_ALPHA_DOWN, "Change sort order"),
            ToolIcon(CoreUiFree.MAGNIFYING_GLASS, "Search backlinks")
        )
    )

    private fun mentions() = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = EmptyBorder(4, 14, 4, 14)

        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            add(caption("Linked mentions", size = 13f, color = TEXT_FG, bold = true), BorderLayout.WEST)
            add(caption("0", size = 12f), BorderLayout.EAST)
        })
        add(Box.createRigidArea(Dimension(0, 8)))
        add(row(FlowLayout.LEFT, 0, 0, caption("No backlinks found.", size = 12f)))
        add(Box.createRigidArea(Dimension(0, 18)))
        add(row(FlowLayout.LEFT, 0, 0, caption("Unlinked mentions", size = 13f)))
    }
}

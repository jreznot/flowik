package demo.swing.obsidian

import flowik.core.action
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.border.EmptyBorder

/**
 * A borderless [Ikon] button with Obsidian's rounded hover / active highlight.
 *
 * [active] is a plain property rather than an observable parameter, because a
 * button is highlighted for many different reasons — a visible panel, a
 * selected tool, an enabled filter. Bind it where it is used:
 *
 * ```kotlin
 * val button = IconButton(CoreUiFree.FILTER, "Filter") { visible.toggle() }
 * button.autoRun { button.active = visible.value }
 * ```
 */
class IconButton(
    private val ikon: Ikon,
    tooltip: String,
    private val iconSize: Int = 16,
    padding: Insets = Insets(6, 7, 6, 7),
    onClick: (() -> Unit)? = null
) : JButton() {

    private var hover = false

    /** Draws the button as pressed-in, the way Obsidian marks the open panel. */
    var active: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            icon = FontIcon.of(ikon, iconSize, if (value) ICON_FG_ACTIVE else ICON_FG)
            repaint()
        }

    init {
        icon = FontIcon.of(ikon, iconSize, ICON_FG)
        toolTipText = tooltip
        border = EmptyBorder(padding)
        isFocusable = false
        isFocusPainted = false
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        // Every click is an action, so a handler that writes several
        // observables notifies the UI once.
        onClick?.let { handler -> addActionListener { action { handler() } } }

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                hover = true
                repaint()
            }

            override fun mouseExited(e: MouseEvent) {
                hover = false
                repaint()
            }
        })
    }

    /** Keeps the button from stretching inside a `BoxLayout` toolbar. */
    override fun getMaximumSize(): Dimension = preferredSize

    override fun paintComponent(g: Graphics) {
        if (hover || active) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = if (active) ACTIVE_BG else HOVER_BG
            g2.fillRoundRect(0, 0, width, height, 8, 8)
            g2.dispose()
        }
        super.paintComponent(g)
    }
}

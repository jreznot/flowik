package demo.swing.obsidian

import flowik.core.action
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/** An icon with a tooltip — what toolbars and the side rail are made of. */
data class ToolIcon(val ikon: Ikon, val title: String)

/** A purple, underline-on-hover link label — Obsidian's in-editor link style. */
fun linkLabel(
    text: String,
    ikon: Ikon? = null,
    fontSize: Float = 15f,
    onClick: () -> Unit
): JLabel = JLabel(text).apply {
    foreground = ACCENT
    font = font.deriveFont(Font.PLAIN, fontSize)
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    horizontalAlignment = SwingConstants.CENTER
    alignmentX = Component.CENTER_ALIGNMENT
    ikon?.let {
        icon = FontIcon.of(it, fontSize.toInt(), ACCENT)
        iconTextGap = 8
        horizontalTextPosition = SwingConstants.LEADING
    }
    addMouseListener(object : MouseAdapter() {
        override fun mouseEntered(e: MouseEvent) {
            this@apply.text = "<html><u>$text</u></html>"
        }

        override fun mouseExited(e: MouseEvent) {
            this@apply.text = text
        }

        override fun mouseClicked(e: MouseEvent) = action { onClick() }
    })
}

/** A small, quiet label — section headers, counters, hints. */
internal fun caption(
    text: String,
    size: Float = 11f,
    color: Color = TEXT_MUTED,
    bold: Boolean = false
) = JLabel(text).apply {
    foreground = color
    font = font.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)
}

/** A transparent single-line row of components. */
internal fun row(
    align: Int = FlowLayout.LEFT,
    hgap: Int = 2,
    vgap: Int = 4,
    vararg items: JComponent
) = JPanel(FlowLayout(align, hgap, vgap)).apply {
    isOpaque = false
    items.forEach { add(it) }
}

/** Builds a row of purely decorative icon buttons. */
internal fun toolIconRow(align: Int, vgap: Int, icons: List<ToolIcon>, vararg trailing: JComponent) =
    row(align, 2, vgap, *(icons.map { IconButton(it.ikon, it.title) } + trailing).toTypedArray())

internal fun verticalSeparator() = JPanel().apply {
    background = LINE
    preferredSize = Dimension(1, 18)
    maximumSize = preferredSize
}

internal fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

/** `⌘N` on macOS, `Ctrl+N` elsewhere. */
fun shortcutHint(key: String): String =
    if (System.getProperty("os.name").startsWith("Mac")) "⌘$key" else "Ctrl+$key"

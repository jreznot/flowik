package demo.swing.obsidian

import flowik.core.ObservableEntity
import flowik.swing.text
import flowik.swing.value
import org.kordamp.ikonli.coreui.CoreUiFree
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/**
 * The editor for one note: an inline title bar over a text area bound two-way
 * to the note's `content` atom.
 *
 * Because the text lives in the observable and not in the widget, an editor can
 * be thrown away and rebuilt — as it is when a tab is closed and reopened —
 * without losing what was typed.
 */
class NoteEditor(private val note: ObservableEntity<Note>) : JPanel(BorderLayout()) {

    private val textArea = JTextArea().apply {
        value(note.property(Note::content))
        lineWrap = true
        wrapStyleWord = true
        background = BG_EDITOR
        foreground = TEXT_FG
        caretColor = ACCENT
        font = font.deriveFont(Font.PLAIN, 14f)
        border = EmptyBorder(4, 28, 24, 28)
    }

    init {
        background = BG_EDITOR
        add(header(), BorderLayout.NORTH)
        add(
            JScrollPane(textArea).apply {
                border = null
                viewport.background = BG_EDITOR
            },
            BorderLayout.CENTER
        )
    }

    fun focusEditor() {
        textArea.requestFocusInWindow()
    }

    private fun header(): JPanel {
        val inlineTitle = JLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = TEXT_FG
            font = font.deriveFont(Font.PLAIN, 13f)
            text { note[Note::name].removeSuffix(".md") }
        }

        return JPanel(BorderLayout()).apply {
            background = BG_EDITOR
            border = EmptyBorder(2, 8, 2, 8)
            add(
                row(
                    FlowLayout.LEFT, 1, 2,
                    IconButton(CoreUiFree.ARROW_LEFT, "Navigate back", iconSize = 14),
                    IconButton(CoreUiFree.ARROW_RIGHT, "Navigate forward", iconSize = 14)
                ),
                BorderLayout.WEST
            )
            add(inlineTitle, BorderLayout.CENTER)
            add(
                row(
                    FlowLayout.RIGHT, 1, 2,
                    IconButton(CoreUiFree.OPTIONS, "More options", iconSize = 14)
                ),
                BorderLayout.EAST
            )
        }
    }
}

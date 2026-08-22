package demo.swing.obsidian

import flowik.core.ObservableEntity
import flowik.swing.text
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel

/** A tab header: file icon, live note name, close button. */
class NoteTabHeader(
    note: ObservableEntity<Note>,
    onClose: () -> Unit
) : JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)) {

    init {
        isOpaque = false

        add(JLabel(FontIcon.of(CoreUiFree.FILE, 13, ICON_FG)).apply {
            iconTextGap = 6
            foreground = TEXT_FG
            text { note[Note::name] }
        })

        add(
            IconButton(
                CoreUiFree.X,
                "Close tab",
                iconSize = 10,
                padding = Insets(3, 3, 3, 3),
                onClick = onClose
            )
        )
    }
}

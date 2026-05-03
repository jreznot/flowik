package flowik.swing

import flowik.core.action
import flowik.layout.PanelScope
import javax.swing.JButton

fun PanelScope.Button(label: String, onClick: () -> Unit): JButton {
    return JButton(label).also {
        panel.add(it)

        it.addActionListener {
            action { onClick() }
        }
    }
}

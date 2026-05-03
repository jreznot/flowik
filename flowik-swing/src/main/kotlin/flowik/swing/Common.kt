package flowik.swing

import java.util.function.Supplier
import javax.swing.JComponent

fun JComponent.enabled(provider: Supplier<Boolean>) {
    autoRun("JComponent.isEnabled") {
        val shouldBeEnabled = provider.get()
        if (isEnabled != shouldBeEnabled) {
            isEnabled = shouldBeEnabled
            repaint()
        }
    }
}

fun JComponent.visible(provider: Supplier<Boolean>) {
    autoRun("JComponent.isVisible") {
        val shouldBeVisible = provider.get()
        if (isVisible != shouldBeVisible) {
            isVisible = shouldBeVisible
            parent?.revalidate()
            parent?.repaint()
        }
    }
}
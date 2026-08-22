package flowik.swing

import flowik.core.Bindings
import java.util.function.Supplier
import javax.swing.JComponent

context(bindings: Bindings)
fun JComponent.enabled(provider: Supplier<Boolean>) {
    bindings.autoRun("JComponent.isEnabled") {
        val shouldBeEnabled = provider.get()
        if (isEnabled != shouldBeEnabled) {
            isEnabled = shouldBeEnabled
            repaint()
        }
    }
}

context(bindings: Bindings)
fun JComponent.visible(provider: Supplier<Boolean>) {
    bindings.autoRun("JComponent.isVisible") {
        val shouldBeVisible = provider.get()
        if (isVisible != shouldBeVisible) {
            isVisible = shouldBeVisible
            parent?.revalidate()
            parent?.repaint()
        }
    }
}

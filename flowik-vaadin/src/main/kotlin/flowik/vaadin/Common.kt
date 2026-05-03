package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasEnabled
import com.vaadin.flow.component.HasText
import flowik.core.autoRun
import java.util.function.Supplier

fun HasEnabled.enabled(provider: Supplier<Boolean>) {
    autoRun("HasEnabled.enabled") {
        isEnabled = provider.get()
    }
}

fun HasText.text(provider: Supplier<String>) {
    autoRun("HasText.text") {
        text = provider.get()
    }
}

fun Component.visible(provider: Supplier<Boolean>) {
    autoRun("Component.visible") {
        val shouldBeVisible = provider.get()
        if (isVisible != shouldBeVisible) {
            isVisible = shouldBeVisible
        }
    }
}
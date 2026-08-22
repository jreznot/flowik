package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasEnabled
import com.vaadin.flow.component.HasLabel
import com.vaadin.flow.component.HasStyle
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.shared.HasTooltip
import flowik.core.Bindings
import java.util.function.Supplier

context(bindings: Bindings)
fun HasEnabled.enabled(provider: Supplier<Boolean>) {
    bindings.autoRun("HasEnabled.enabled") {
        isEnabled = provider.get()
    }
}

context(bindings: Bindings)
fun HasText.text(provider: Supplier<String>) {
    bindings.autoRun("HasText.text") {
        text = provider.get()
    }
}

context(bindings: Bindings)
fun HasLabel.label(provider: Supplier<String>) {
    bindings.autoRun("HasLabel.label") {
        label = provider.get()
    }
}

context(bindings: Bindings)
fun Component.visible(provider: Supplier<Boolean>) {
    bindings.autoRun("Component.visible") {
        val shouldBeVisible = provider.get()
        if (isVisible != shouldBeVisible) {
            isVisible = shouldBeVisible
        }
    }
}

context(bindings: Bindings)
fun HasStyle.className(provider: Supplier<String>) {
    bindings.autoRun("HasStyle.className") {
        className = provider.get()
    }
}

context(bindings: Bindings)
fun HasStyle.classNames(provider: Supplier<List<String>>) {
    bindings.autoRun("HasStyle.classNames") {
        className = provider.get().joinToString(" ")
    }
}

context(bindings: Bindings)
fun HasTooltip.tooltipText(provider: Supplier<String>) {
    bindings.autoRun("HasTooltip.tooltipText") {
        setTooltipText(provider.get())
    }
}

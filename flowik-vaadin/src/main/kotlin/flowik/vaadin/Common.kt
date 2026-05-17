package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasEnabled
import com.vaadin.flow.component.HasLabel
import com.vaadin.flow.component.HasStyle
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.shared.HasTooltip
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

fun HasLabel.label(provider: Supplier<String>) {
    autoRun("HasLabel.label") {
        label = provider.get()
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

fun HasStyle.className(provider: Supplier<String>) {
    autoRun("HasStyle.className") {
        className = provider.get()
    }
}

fun HasStyle.classNames(provider: Supplier<List<String>>) {
    autoRun("HasStyle.classNames") {
        className = provider.get().joinToString(" ")
    }
}

fun HasTooltip.tooltipText(provider: Supplier<String>) {
    autoRun("HasTooltip.tooltipText") {
        setTooltipText(provider.get())
    }
}
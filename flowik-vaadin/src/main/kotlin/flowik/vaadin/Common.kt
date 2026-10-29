package flowik.vaadin

import com.vaadin.flow.component.ClickNotifier
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Focusable
import com.vaadin.flow.component.HasEnabled
import com.vaadin.flow.component.HasLabel
import com.vaadin.flow.component.HasStyle
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.shared.HasTooltip
import com.vaadin.flow.shared.Registration
import flowik.core.Bindings
import flowik.core.action
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

/**
 * Adds or removes the single class [name], leaving the component's other
 * classes alone — the way to mark a row `selected` or a field `invalid`.
 */
context(bindings: Bindings)
fun HasStyle.className(name: String, provider: Supplier<Boolean>) {
    bindings.autoRun("HasStyle.className($name)") {
        setClassName(name, provider.get())
    }
}

/**
 * Marks the current item of a list, a tab strip or a menu: toggles the
 * `active` class and keeps `aria-selected` in step, so the highlight and what
 * a screen reader announces never disagree. Pass [className] to use another
 * class.
 */
context(bindings: Bindings)
fun Component.activeWhen(className: String = "active", provider: Supplier<Boolean>) {
    bindings.autoRun("Component.activeWhen") {
        val active = provider.get()
        element.classList.set(className, active)
        element.setAttribute("aria-selected", active.toString())
    }
}

/**
 * Moves the caret into this field each time [provider] turns `true` — a
 * search box revealed by a button, an inline editor that opens. It does not
 * blur the field when [provider] turns `false` again.
 */
context(bindings: Bindings)
fun Focusable<*>.focusWhen(provider: Supplier<Boolean>) {
    var wanted = false
    bindings.autoRun("Focusable.focusWhen") {
        val now = provider.get()
        if (now && !wanted) focus()
        wanted = now
    }
}

/** Sets one inline style [property]; `null` removes it. */
context(bindings: Bindings)
fun HasStyle.style(property: String, provider: Supplier<String?>) {
    bindings.autoRun("HasStyle.style($property)") {
        val value = provider.get()
        if (value == null) style.remove(property) else style.set(property, value)
    }
}

/** Sets one element attribute — `aria-*`, `title`, `theme`…; `null` removes it. */
context(bindings: Bindings)
fun Component.attribute(name: String, provider: Supplier<String?>) {
    bindings.autoRun("Component.attribute($name)") {
        val value = provider.get()
        if (value == null) element.removeAttribute(name) else element.setAttribute(name, value)
    }
}

/**
 * Click listener for anything clickable — a card, a list row, a `Div` — with
 * the handler run as an action, as [Button] does.
 */
fun <C : Component> ClickNotifier<C>.onClick(handler: () -> Unit): Registration =
    addClickListener { action { handler() } }

context(bindings: Bindings)
fun HasTooltip.tooltipText(provider: Supplier<String>) {
    bindings.autoRun("HasTooltip.tooltipText") {
        setTooltipText(provider.get())
    }
}

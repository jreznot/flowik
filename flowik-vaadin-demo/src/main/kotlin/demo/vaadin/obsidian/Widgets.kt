package demo.vaadin.obsidian

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.html.Anchor
import com.vaadin.flow.component.html.AnchorTarget
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.shared.Tooltip
import flowik.vaadin.onClick
import flowik.vaadin.onDomEvent

/** An icon with a tooltip — what toolbars and the side rail are made of. */
data class ToolIcon(val icon: VaadinIcon, val title: String)

/**
 * A borderless icon button with Obsidian's rounded hover / active highlight.
 *
 * Whether it is drawn *active* — pressed-in, the way Obsidian marks the open
 * panel — is up to the caller, because a button is highlighted for many
 * different reasons. Bind it where it is used:
 *
 * ```kotlin
 * IconButton(VaadinIcon.FILTER, "Filter") { visible.toggle() }
 *     .apply { className("active") { visible.value } }
 * ```
 *
 * Without [onClick] the button is purely decorative, as most of the chrome in
 * this demo is.
 */
fun IconButton(
    icon: VaadinIcon,
    tooltip: String,
    small: Boolean = false,
    onClick: (() -> Unit)? = null
): NativeButton = NativeButton().apply {
    addClassName("obs-icon-button")
    if (small) addClassName("small")
    add(icon.create())
    element.setAttribute("aria-label", tooltip)
    Tooltip.forComponent(this).withText(tooltip)

    // Every click is an action, so a handler that writes several observables
    // notifies the UI once.
    onClick?.let { this.onClick(it) }
}

/** A purple, underline-on-hover link — Obsidian's in-editor link style. */
fun LinkLabel(text: String, onClick: () -> Unit): Span = Span(text).apply {
    addClassName("obs-link")
    element.setAttribute("role", "link")
    element.setAttribute("tabindex", "0")
    this.onClick(onClick)
    onDomEvent("keydown", filter = "event.key === 'Enter'") { onClick() }
}

/** The same link style for a real URL, opened in a new browser tab. */
fun ExternalLink(text: String, href: String): Anchor = Anchor(href, text, AnchorTarget.BLANK).apply {
    addClassName("obs-link")
    add(VaadinIcon.EXTERNAL_LINK.create())
}

/** A small, quiet label — section headers, counters, hints. */
fun Caption(text: String = "", vararg classNames: String): Span = Span(text).apply {
    addClassName("obs-caption")
    addClassNames(*classNames)
}

/** A transparent single-line row of components. */
fun Row(vararg items: Component): Div = Div(*items).apply { addClassName("obs-row") }

/** Builds a row of purely decorative icon buttons. */
fun ToolIconRow(icons: List<ToolIcon>, vararg trailing: Component): Div =
    Row(*(icons.map { IconButton(it.icon, it.title) } + trailing).toTypedArray())

fun VerticalSeparator(): Div = Div().apply { addClassName("obs-separator") }

/**
 * The shortcut as the user types it. Browsers keep ⌘/Ctrl+N, W and O for
 * themselves, so the web version of the demo binds them to Alt instead.
 */
fun shortcutHint(key: String): String = "Alt+$key"

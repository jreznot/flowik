package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.KeyModifier
import com.vaadin.flow.component.ShortcutRegistration
import com.vaadin.flow.component.Shortcuts
import com.vaadin.flow.dom.DomEvent
import com.vaadin.flow.dom.DomListenerRegistration
import com.vaadin.flow.server.Command
import flowik.core.action

/**
 * Listens for the DOM event [type] on this component's element and runs
 * [handler] as an action, as [onClick] does for a `ClickEvent`.
 *
 * [filter] is a JavaScript expression the *browser* evaluates, with `event`
 * and `element` in scope; only events it passes are sent to the server. That
 * is how a handler ignores the events it does not care about without a round
 * trip for each:
 *
 * ```kotlin
 * row.onDomEvent("keydown", filter = "event.key === 'Enter'") { open(note) }
 * // A click on the tab, but not on its close button.
 * tab.onDomEvent("click", filter = "!event.target.closest('.close')") { select(note) }
 * ```
 *
 * The listener lives as long as the element, like any Vaadin listener; remove
 * it through the returned registration.
 */
fun Component.onDomEvent(
    type: String,
    filter: String? = null,
    handler: (DomEvent) -> Unit
): DomListenerRegistration =
    element.addEventListener(type) { event -> action { handler(event) } }
        .apply { filter?.let { setFilter(it) } }

/**
 * Runs [handler] once the CSS animation [animationName] has finished on this
 * component itself — not on a child, whose own `animationend` bubbles up.
 *
 * It is the second half of an exit animation that has to finish *before* the
 * model changes: add the class that starts the animation, and change the state
 * here, so the component is gone on screen by the time it is gone from the
 * model.
 *
 * ```kotlin
 * tab.onAnimationEnd("tab-collapse") { store.close(note) }
 * tab.addClassName("closing")       // .closing { animation: tab-collapse 170ms }
 * ```
 *
 * With `prefers-reduced-motion`, give the animation a near-zero duration
 * rather than `animation: none` — an animation that never runs never ends.
 */
fun Component.onAnimationEnd(animationName: String, handler: () -> Unit): DomListenerRegistration =
    onDomEvent(
        "animationend",
        filter = "event.target === element && event.animationName === '$animationName'"
    ) { handler() }

/**
 * A keyboard shortcut that runs [handler] as an action for as long as this
 * component is attached — it is the shortcut's lifecycle owner. By default it
 * listens on the whole UI; narrow that with `listenOn(…)` on the result.
 *
 * ```kotlin
 * shortcut(Key.KEY_N, KeyModifier.ALT) { store.createNote() }
 * ```
 *
 * Browsers keep some combinations for themselves — Ctrl/⌘ with N, T or W —
 * and never hand them to the page.
 */
fun Component.shortcut(key: Key, vararg modifiers: KeyModifier, handler: () -> Unit): ShortcutRegistration =
    Shortcuts.addShortcutListener(this, Command { action { handler() } }, key, *modifiers)

package flowik.vaadin.testing

import com.vaadin.flow.component.AbstractField
import com.vaadin.flow.component.ClickEvent
import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentUtil
import com.vaadin.flow.dom.DomEvent
import com.vaadin.flow.internal.JacksonUtils
import com.vaadin.flow.internal.nodefeature.ElementListenerMap

/*
 * Helpers for testing Flowik-bound Vaadin views on the server, without a
 * browser: find components, check what the user can see, and send the events
 * a browser would — clicks, DOM events, edits typed into a field.
 *
 *     testImplementation(testFixtures(project(":flowik-vaadin")))
 *
 * Bindings only write back what comes *from the client*, so a test that sets
 * `field.value` directly checks nothing; [setValueFromClient] is the edit the
 * binding listens for.
 */

/** Every component below this one, depth first, in document order. */
fun Component.descendants(): List<Component> =
    children.toList().flatMap { listOf(it) + it.descendants() }

/** The components of type [T] below this one. */
inline fun <reified T : Component> Component.find(): List<T> = descendants().filterIsInstance<T>()

/** The components below this one that carry [className]. */
fun Component.findByClass(className: String): List<Component> = descendants().filter { it.hasClass(className) }

fun Component.hasClass(className: String): Boolean = element.classList.contains(className)

/** Visible itself and inside nothing hidden — what the user can actually see. */
fun Component.isShown(): Boolean = isVisible && parent.map { it.isShown() }.orElse(true)

/**
 * Fires a click as `addClickListener` and `onClick` receive it. (Not named
 * `click`, which `Button` has as a member of its own.)
 */
fun Component.simulateClick() {
    ComponentUtil.fireEvent(this, ClickEvent(this))
}

/**
 * Sends a DOM event the way the browser would, to the listeners registered with
 * `element.addEventListener` — `onDomEvent`, `onAnimationEnd`.
 *
 * The browser evaluates each listener's filter and sends the result along, and
 * the server runs only the listeners whose filter passed. [filtersPass] stands
 * for what the browser decided — for all of them at once.
 */
fun Component.fireDomEvent(type: String, filtersPass: Boolean = true) {
    val listeners = element.node.getFeature(ElementListenerMap::class.java)
    val data = JacksonUtils.createObjectNode()
    listeners.getExpressions(type).forEach { data.put(it, filtersPass) }
    listeners.fireEvent(DomEvent(element, type, data))
}

private val setModelValue = AbstractField::class.java
    .getDeclaredMethod("setModelValue", Any::class.java, Boolean::class.javaPrimitiveType)
    .apply { isAccessible = true }

/**
 * Changes the field's value as if the user had typed or picked it: one value
 * change event, marked as coming from the client — the one Flowik's `value`
 * bindings write back to the model.
 */
fun <C : AbstractField<C, V>, V> C.setValueFromClient(value: V) {
    setModelValue.invoke(this, value, true)
}

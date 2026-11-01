package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.contextmenu.HasMenuItems
import com.vaadin.flow.component.contextmenu.MenuItem
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.untracked
import java.util.function.Supplier

/**
 * Adds an item to a `MenuBar`, a sub-menu or a `ContextMenu` that runs
 * [onClick] as an action, as [Button] does.
 *
 * A [MenuItem] is a regular component, so the usual bindings apply to it:
 *
 * ```kotlin
 * val more = menuBar.addItem("More").subMenu
 * more.item("Delete card") { store.deleteCard(card) }
 *     .apply { enabled { store.canDelete } }
 * ```
 */
fun HasMenuItems.item(text: String, onClick: () -> Unit): MenuItem =
    addItem(text) { action { onClick() } }

/** The same, for an item that shows a component — an icon and a caption, say. */
fun HasMenuItems.item(component: Component, onClick: () -> Unit): MenuItem =
    addItem(component) { action { onClick() } }

/**
 * Makes this item checkable and binds its check mark two-way to [model]: the
 * mark follows the model, and a click flips it.
 */
context(bindings: Bindings)
fun MenuItem.checked(model: MutableObservable<Boolean>) =
    checked(model) { model.value = it }

/**
 * The controlled form: the item shows [checked], and a click reports the
 * opposite to [onCheckedChange], which runs as an action. If the callback does
 * not change the state, the mark stays where [checked] says it is.
 */
context(bindings: Bindings)
fun MenuItem.checked(checked: Supplier<Boolean>, onCheckedChange: (Boolean) -> Unit) {
    isCheckable = true
    bindings.autoRun("MenuItem.checked") {
        isChecked = checked.get()
    }
    // The item flips its own mark on the click before this listener runs, so
    // the new state is worked out from the model, and the mark set last.
    addClickListener {
        action { onCheckedChange(!untracked { checked.get() }) }
        isChecked = untracked { checked.get() }
    }
}

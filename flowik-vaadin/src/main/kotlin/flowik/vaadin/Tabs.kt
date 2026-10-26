package flowik.vaadin

import com.vaadin.flow.component.tabs.Tab
import com.vaadin.flow.component.tabs.Tabs
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.action
import flowik.core.untracked
import java.util.function.Supplier

/**
 * Adds one [Tab] per option and keeps the selected tab and [selection] in step,
 * both ways.
 *
 * The selection is a value of your own — typically an enum — rather than an
 * index, so the content below the tabs can switch on it directly:
 *
 * ```kotlin
 * Tabs().apply { items(Section.entries, store.section) { it.title } }
 * Div().apply { childSwitch(store.section) { section -> … } }
 * ```
 */
context(bindings: Bindings)
fun <T> Tabs.items(
    options: List<T>,
    selection: MutableObservable<T>,
    label: (T) -> String = { it.toString() }
) = items(options, selection, { selection.value = it }, label)

/**
 * The controlled form: the tabs show [selected] and report a click to
 * [onSelect], which runs as an action. A click the callback does not act on
 * leaves the selection where [selected] says it is.
 */
context(bindings: Bindings)
fun <T> Tabs.items(
    options: List<T>,
    selected: Supplier<T>,
    onSelect: (T) -> Unit,
    label: (T) -> String = { it.toString() }
) {
    val tabs = options.map { Tab(label(it)) }
    // Tabs autoselect the first one added; that happens before the listener
    // below exists, so it is not reported as a selection.
    add(*tabs.toTypedArray())

    fun showSelected(current: T) {
        val index = options.indexOf(current)
        if (index >= 0 && selectedIndex != index) {
            selectedIndex = index
        }
    }

    bindings.autoRun("Tabs.selection") { showSelected(selected.get()) }
    addSelectedChangeListener { event ->
        val index = tabs.indexOf(event.selectedTab)
        if (index >= 0 && options[index] != untracked { selected.get() }) {
            action { onSelect(options[index]) }
            showSelected(untracked { selected.get() })
        }
    }
}

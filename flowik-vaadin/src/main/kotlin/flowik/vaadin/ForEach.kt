package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import flowik.core.Bindings
import flowik.core.ListChange
import flowik.core.ObservableList
import java.util.function.Supplier

/**
 * Keeps this container's children in step with [list], one component per item.
 *
 * A component dropped here is disposed if it owns bindings, so [map] is free to
 * return a component that mixed in [Bindings].
 */
context(bindings: Bindings)
fun <T, L> L.items(list: ObservableList<T>, map: (T) -> Component)
        where L : Component, L : HasComponents {
    val children = mutableListOf<Component>()
    for (item in list.items) {
        val comp = map(item)
        children.add(comp)
        add(comp)
    }

    bindings.register(list.onChange { change ->
        when (change) {
            is ListChange.Insert -> {
                val comp = map(change.item)
                children.add(change.index, comp)
                addComponentAtIndex(change.index, comp)
            }

            is ListChange.Remove -> {
                val comp = children.removeAt(change.index)
                remove(comp)
                disposeIfOwned(comp)
            }

            is ListChange.Update -> {
                val old = children[change.index]
                val new = map(change.new)
                replace(old, new)
                children[change.index] = new
                disposeIfOwned(old)
            }

            is ListChange.Clear -> {
                children.forEach { disposeIfOwned(it) }
                children.clear()
                removeAll()
            }
        }
    })
}

context(bindings: Bindings)
fun <T, L> L.items(computedList: Supplier<List<T>>, map: (T) -> Component)
        where L : Component, L : HasComponents {
    val children = mutableListOf<Component>()
    bindings.autoRun("VForEach.items") {
        children.forEach { disposeIfOwned(it) }
        children.clear()
        removeAll()
        for (item in computedList.get()) {
            val comp = map(item)
            children.add(comp)
            add(comp)
        }
    }
}

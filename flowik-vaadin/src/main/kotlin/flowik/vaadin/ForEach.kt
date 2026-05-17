package flowik.vaadin

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import flowik.core.ListChange
import flowik.core.ObservableList
import java.util.function.Supplier

fun <T, L> L.items(list: ObservableList<T>, map: (T) -> Component)
        where L : Component, L : HasComponents {
    val children = mutableListOf<Component>()
    for (item in list.items) {
        val comp = map(item)
        children.add(comp)
        add(comp)
    }

    val subscription = list.onChange { change ->
        when (change) {
            is ListChange.Insert -> {
                val comp = map(change.item)
                children.add(change.index, comp)
                addComponentAtIndex(change.index, comp)
            }

            is ListChange.Remove -> {
                val comp = children.removeAt(change.index)
                remove(comp)
            }

            is ListChange.Update -> {
                val old = children[change.index]
                val new = map(change.new)
                replace(old, new)
                children[change.index] = new
            }

            is ListChange.Clear -> {
                children.clear()
                removeAll()
            }
        }
    }

    onDetached { subscription.dispose() }
}

fun <T, L> L.items(computedList: Supplier<List<T>>, map: (T) -> Component)
        where L : Component, L : HasComponents {
    autoRun("VForEach.items") {
        removeAll()
        for (item in computedList.get()) {
            add(map(item))
        }
    }
}

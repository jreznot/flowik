package reaktor.swing

import reaktor.core.Derived
import reaktor.core.ObservableValue
import reaktor.layout.PanelScope
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A reactive JPanel that displays a single child component whose identity is
 * determined by mapping an observed value through a mapping function.
 *
 * Call [bind] to connect the observable source and the mapping function.
 * Whenever the value changes the old child is removed and a new one is added.
 */
class RSwitch<T> : JPanel(), RComponent {

    init { layout = BorderLayout() }

    fun bind(observe: () -> T, map: (T) -> JComponent) {
        var lastValue: Any? = Unset
        var currentChild: JComponent? = null
        autoReaction("RSwitch") {
            val value = observe()
            if (value != lastValue) {
                lastValue = value
                currentChild?.let { remove(it) }
                val child = map(value)
                currentChild = child
                add(child, BorderLayout.CENTER)
                revalidate()
                repaint()
            }
        }
    }

    fun bind(observable: ObservableValue<T>, map: (T) -> JComponent) =
        bind({ observable.value }, map)

    fun bind(derived: Derived<T>, map: (T) -> JComponent) =
        bind({ derived.value }, map)

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

private object Unset

fun <T> PanelScope.Switch(
    observable: ObservableValue<T>,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch<T>().also { it.bind(observable, map); panel.add(it) }

fun <T> PanelScope.Switch(
    derived: Derived<T>,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch<T>().also { it.bind(derived, map); panel.add(it) }

fun <T> PanelScope.Switch(
    observe: () -> T,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch<T>().also { it.bind(observe, map); panel.add(it) }

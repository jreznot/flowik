package reaktor.swing

import reaktor.core.Derived
import reaktor.core.ObservableValue
import reaktor.layout.PanelScope
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A reactive JPanel that displays a single child component whose identity is
 * determined by mapping an observed value through [map].
 *
 * Whenever the observed value changes the old child is removed and the new one
 * (produced by [map]) is added in its place.  The reaction is tied to the
 * component lifecycle and is disposed automatically when the panel is removed
 * from the hierarchy.
 *
 * @param observe Lambda that reads any reactive source (ObservableValue, Derived,
 *                or arbitrary computed expression).
 * @param map     Produces the [JComponent] to display for the current value.
 */
class RSwitch<T>(
    observe: () -> T,
    map: (T) -> JComponent
) : JPanel(), RComponent {

    init {
        layout = BorderLayout()

        // Sentinel avoids a needless map() call on first run vs. "previous value
        // was null" ambiguity when T is nullable.
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

    override fun removeNotify() {
        super<JPanel>.removeNotify()
        super<RComponent>.removeNotify()
    }
}

private object Unset

// ---------------------------------------------------------------------------
// DSL builders
// ---------------------------------------------------------------------------

/**
 * Adds an [RSwitch] driven by an [ObservableValue].
 *
 * Example:
 * ```kotlin
 * rswitch(store.currentPage) { page ->
 *     when (page) {
 *         Page.HOME     -> HomePanel()
 *         Page.SETTINGS -> SettingsPanel()
 *     }
 * }
 * ```
 */
fun <T> PanelScope.Switch(
    observable: ObservableValue<T>,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch({ observable.value }, map).also { panel.add(it) }

/**
 * Adds an [RSwitch] driven by a [Derived] value.
 */
fun <T> PanelScope.Switch(
    derived: Derived<T>,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch({ derived.value }, map).also { panel.add(it) }

/**
 * Adds an [RSwitch] driven by an arbitrary reactive lambda.
 * Any observables read inside [observe] are tracked automatically.
 */
fun <T> PanelScope.Switch(
    observe: () -> T,
    map: (T) -> JComponent
): RSwitch<T> = RSwitch(observe, map).also { panel.add(it) }

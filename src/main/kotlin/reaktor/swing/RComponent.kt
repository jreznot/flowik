package reaktor.swing

import reaktor.core.Reaction
import javax.swing.JComponent

private const val KEY = "reaktor.reactions"

/**
 * Interface mixin for reactive Swing components.
 *
 * Manages reaction lifecycle — all reactions registered via [autoReaction] are
 * disposed when the host component's [removeNotify] is invoked.  Reactions are
 * stored directly on the component via [JComponent.putClientProperty], so
 * implementing classes need no backing field.
 *
 * The implementing class **must** be a [JComponent] subtype.  Its
 * [removeNotify] override should call both the superclass and this interface:
 *
 * ```kotlin
 * override fun removeNotify() {
 *     super.removeNotify()
 *     super<RComponent>.removeNotify()
 * }
 * ```
 */
interface RComponent {

    /** Create a reaction and register it for automatic disposal. */
    fun autoReaction(name: String? = null, effect: () -> Unit): Reaction {
        val r = reaktor.core.reaction(name, effect)
        reactions().add(r)
        return r
    }

    /**
     * Disposes all reactions registered via [autoReaction].
     *
     * Call this from the concrete class's [removeNotify] via
     * `super<RComponent>.removeNotify()`.
     */
    fun removeNotify() {
        val list = reactions()
        list.forEach { it.dispose() }
        list.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun reactions(): MutableList<Reaction> {
        val jc = this as JComponent
        return jc.getClientProperty(KEY) as? MutableList<Reaction>
            ?: mutableListOf<Reaction>().also { jc.putClientProperty(KEY, it) }
    }
}

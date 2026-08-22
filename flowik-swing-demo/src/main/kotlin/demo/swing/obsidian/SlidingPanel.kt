package demo.swing.obsidian

import com.formdev.flatlaf.util.Animator
import com.formdev.flatlaf.util.CubicBezierEasing
import flowik.swing.autoRun
import java.awt.Dimension
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

/** Which window edge a [SlidingPanel] is docked to, and so which way it leaves. */
enum class SlideSide { LEFT, RIGHT }

/**
 * Wraps a docked side panel and slides it in and out of view whenever [open]
 * changes, the way Obsidian's sidebars move.
 *
 * The wrapper is what shrinks: its preferred width follows the animation while
 * the content keeps its full size, pinned to the docked edge. The content is
 * therefore *clipped* rather than re-laid-out, so nothing inside squeezes
 * together on the way out — it simply travels off the edge.
 *
 * Wrapping rather than animating the panel itself keeps the panels reusable:
 * [NotesSidebar] and [BacklinksPanel] know nothing about being collapsible.
 *
 * ```kotlin
 * SlidingPanel(NotesSidebar(…), SlideSide.LEFT, store::leftVisible)
 * ```
 */
class SlidingPanel(
    private val content: JComponent,
    private val side: SlideSide,
    open: Supplier<Boolean>,
    private val duration: Int = PANEL_SLIDE_MS
) : JPanel() {

    /** 0 — fully collapsed, 1 — fully open. */
    private var fraction = 0f
    private var animator: Animator? = null

    init {
        isOpaque = false
        layout = null
        add(content)

        var firstRun = true
        autoRun("slidingPanel.open") {
            val target = if (open.get()) 1f else 0f
            if (firstRun) {
                // Opening state at start-up is the layout, not an animation.
                firstRun = false
                fraction = target
                content.isVisible = target > 0f
            } else {
                slideTo(target)
            }
        }
    }

    private fun slideTo(target: Float) {
        animator?.cancel()
        content.isVisible = true
        animator = animateValue(
            duration, fraction, target, CubicBezierEasing.EASE_IN_OUT,
            onFrame = {
                fraction = it
                reflow()
            },
            onEnd = {
                content.isVisible = fraction > 0f
                reflow()
            }
        )
    }

    private fun reflow() {
        revalidate()
        // The neighbour that grows into the freed space has to be repainted too.
        parent?.repaint()
    }

    private fun contentWidth() = content.preferredSize.width

    override fun getPreferredSize() = Dimension(Math.round(contentWidth() * fraction), 0)

    override fun getMinimumSize() = preferredSize

    override fun getMaximumSize() = Dimension(contentWidth(), Int.MAX_VALUE)

    override fun doLayout() {
        // Pin the content to the docked edge: the left panel's right edge stays
        // put as the wrapper narrows, so the panel appears to travel left.
        val contentWidth = contentWidth()
        val x = if (side == SlideSide.LEFT) width - contentWidth else 0
        content.setBounds(x, 0, contentWidth, height)
    }
}

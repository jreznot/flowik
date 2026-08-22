package demo.swing.obsidian

import com.formdev.flatlaf.util.Animator
import com.formdev.flatlaf.util.CubicBezierEasing
import flowik.core.ObservableEntity
import flowik.swing.BindingsPanel
import flowik.swing.text
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * A tab header — file icon, live note name, close button — that grows into
 * place instead of appearing at full size.
 *
 * A `JTabbedPane` sizes a tab from the preferred width of its tab component, so
 * animating that width animates the tab itself. As with [SlidingPanel] the row
 * inside keeps its natural size and is clipped, which reveals the name
 * left-to-right rather than compressing it.
 */
class NoteTabHeader(
    note: ObservableEntity<Note>,
    private val duration: Int = TAB_GROW_MS,
    onClose: () -> Unit
) : BindingsPanel(null) {

    private val content = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        isOpaque = false
        // The horizontal padding a tab normally gets from `TabbedPane.tabInsets`
        // lives here instead, so that a collapsing tab can reach zero width.
        border = EmptyBorder(0, 8, 0, 4)

        add(JLabel(FontIcon.of(CoreUiFree.FILE, 13, ICON_FG)).apply {
            iconTextGap = 6
            foreground = TEXT_FG
            text { note[Note::name] }
        })

        add(
            IconButton(
                CoreUiFree.X,
                "Close tab",
                iconSize = 10,
                padding = Insets(3, 3, 3, 3),
                onClick = onClose
            )
        )
    }

    /** 0 — no tab at all, 1 — the full title is visible. */
    private var fraction = 0f
    private var animator: Animator? = null

    init {
        isOpaque = false
        add(content)

        // A brand-new tab starts narrow and grows into place.
        val full = contentWidth()
        fraction = if (full <= 0) 1f else (START_WIDTH.toFloat() / full).coerceAtMost(1f)
        expand()
    }

    /**
     * Grows the tab to the full width of its title row, from wherever it is now.
     *
     * Calling this during a [collapse] cancels it — the pending close is dropped
     * along with it, which is how a tab reopened mid-collapse comes back.
     */
    fun expand() {
        animateTo(1f)
    }

    /**
     * Shrinks the tab away and then runs [onFinished] — which is where the
     * caller actually closes the note, so the tab is gone before the model is.
     */
    fun collapse(onFinished: () -> Unit) {
        animateTo(0f, onFinished)
    }

    private fun animateTo(target: Float, onFinished: () -> Unit = {}) {
        animator?.cancel()
        animator = animateValue(
            duration, fraction, target, CubicBezierEasing.EASE_OUT,
            onFrame = {
                fraction = it
                reflow()
            },
            onEnd = onFinished
        )
    }

    private fun reflow() {
        // Invalidating the tab component re-runs the tabbed pane's tab layout.
        revalidate()
        parent?.repaint()
    }

    private fun contentWidth() = content.preferredSize.width

    override fun getPreferredSize() =
        Dimension(Math.round(contentWidth() * fraction), content.preferredSize.height)

    override fun getMinimumSize() = preferredSize

    override fun getMaximumSize() = preferredSize

    override fun doLayout() {
        content.setBounds(0, 0, contentWidth(), height)
    }

    private companion object {
        /** Width a freshly opened tab starts at, before it expands. */
        const val START_WIDTH = 46
    }
}

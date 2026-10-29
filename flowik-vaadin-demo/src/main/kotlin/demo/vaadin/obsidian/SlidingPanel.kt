package demo.vaadin.obsidian

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.html.Div
import flowik.core.Bindings
import flowik.vaadin.className
import java.util.function.Supplier

/** Which window edge a [SlidingPanel] is docked to, and so which way it leaves. */
enum class SlideSide { LEFT, RIGHT }

/**
 * Wraps a docked side panel and slides it in and out of view whenever [open]
 * changes, the way Obsidian's sidebars move.
 *
 * The wrapper is what shrinks: its width transitions in CSS while the content
 * keeps its full [width], pinned to the docked edge. The content is therefore
 * *clipped* rather than re-laid-out, so nothing inside squeezes together on the
 * way out — it simply travels off the edge.
 *
 * Wrapping rather than animating the panel itself keeps the panels reusable:
 * [NotesSidebar] and [BacklinksPanel] know nothing about being collapsible.
 * The binding belongs to the caller's group; whoever built [content] is still
 * the one who releases it.
 *
 * ```kotlin
 * SlidingPanel(NotesSidebar(…), SlideSide.LEFT, store::leftVisible)
 * ```
 */
context(bindings: Bindings)
fun SlidingPanel(content: Component, side: SlideSide, open: Supplier<Boolean>, width: String): Div =
    Div(content).apply {
        addClassNames("obs-slide", if (side == SlideSide.LEFT) "obs-slide-left" else "obs-slide-right")
        style.set("--obs-slide-width", width)
        // Present before the first render, so the opening state at start-up is
        // the layout, not an animation.
        className("collapsed") { !open.get() }
    }

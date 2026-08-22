package demo.swing.obsidian

import com.formdev.flatlaf.util.Animator
import com.formdev.flatlaf.util.CubicBezierEasing

/** How long a panel takes to slide in or out. */
internal const val PANEL_SLIDE_MS = 220

/** How long a tab takes to expand or collapse. */
internal const val TAB_GROW_MS = 170

/**
 * Interpolates from [from] to [to] over [duration], feeding every frame to
 * [onFrame] and running [onEnd] once the value has arrived.
 *
 * Built on FlatLaf's own [Animator], which the look and feel already uses for
 * its own effects — so animations honour the `flatlaf.animation` system
 * property, and the returned animator can be [Animator.cancel]led to re-target
 * mid-flight *without* [onEnd] firing. That distinction matters when the end of
 * an animation triggers a state change, as it does when a tab finishes
 * collapsing.
 *
 * Returns `null` when the value was applied immediately: animations off, or
 * nothing to animate.
 */
internal fun animateValue(
    duration: Int,
    from: Float,
    to: Float,
    interpolator: Animator.Interpolator = CubicBezierEasing.EASE_OUT,
    onFrame: (Float) -> Unit,
    onEnd: () -> Unit = {}
): Animator? {
    if (!Animator.useAnimation() || from == to) {
        onFrame(to)
        onEnd()
        return null
    }

    val distance = to - from
    val animator = Animator(
        duration,
        Animator.TimingTarget { fraction -> onFrame(from + distance * fraction) },
        // Snap to the exact target before handing over: a frame is only ever as
        // precise as the timer that produced it.
        Runnable {
            onFrame(to)
            onEnd()
        }
    )
    animator.interpolator = interpolator
    animator.start()
    return animator
}

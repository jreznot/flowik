package flowik.swing

import flowik.core.Bindings
import flowik.core.Disposable
import java.awt.FlowLayout
import java.awt.LayoutManager
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JPanel

/**
 * A panel that *is* the [Bindings] group of the UI built inside it.
 *
 * Every binding in `flowik.swing` takes a [Bindings] as a context parameter, and
 * a context parameter is satisfied by any receiver in scope — so inside the
 * members of this panel the group needs no argument, no field and no `context`
 * block:
 *
 * ```kotlin
 * class Header(title: ReadableObservable<String>) : BindingsPanel(BorderLayout()) {
 *     init {
 *         add(JLabel().apply { text { title.value } })    // registers here
 *         autoRun("header.dirty") { … }                   // and so does this
 *     }
 * }
 * ```
 *
 * Disposal is explicit: nothing watches the component hierarchy, so a panel
 * moved between containers, hidden behind a `CardLayout` or re-parented keeps
 * working. Release it by
 *
 *  - [dispose] — when the UI is really gone;
 *  - [Bindings.register] on the parent panel — a child then goes with its owner;
 *  - [disposeOnClose] — for the top of the tree, whose lifetime is its window.
 *
 * @param layout the layout manager, defaulting to `JPanel`'s own [FlowLayout];
 *               pass `null` for a panel that lays its children out itself
 */
open class BindingsPanel(
    layout: LayoutManager? = FlowLayout()
) : JPanel(layout), Bindings {

    private val bindings = Bindings()

    override fun <T : Disposable> register(disposable: T): T = bindings.register(disposable)

    /**
     * Releases everything created through this panel. Override it — calling
     * `super.dispose()` — to also release children the panel builds and drops
     * as it runs, which its group never got to see.
     */
    override fun dispose() {
        bindings.dispose()
    }
}

/**
 * Disposes [bindings] once this window has been closed.
 *
 * The window is the one Swing lifecycle worth hooking: a closed window is never
 * shown again, whereas a component leaving the hierarchy usually means a card
 * was switched or a tab reordered.
 *
 * Note that `windowClosed` only fires for a window that is actually disposed —
 * with `JFrame.EXIT_ON_CLOSE` the process goes first, which is fine for an
 * application frame.
 */
fun <W : Window> W.disposeOnClose(bindings: Bindings): W = apply {
    addWindowListener(object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) {
            bindings.dispose()
        }
    })
}

/**
 * Disposes [component] if it owns bindings, i.e. if it is a [BindingsPanel] or
 * any other [Disposable] component.
 *
 * Containers that build their own children — `ForEach`, `Switch` — use this
 * when they drop one: whoever created a component owns it, and a dropped child
 * that kept its reactions alive would go on writing into a panel nobody can see.
 */
internal fun disposeIfOwned(component: Any?) {
    (component as? Disposable)?.dispose()
}

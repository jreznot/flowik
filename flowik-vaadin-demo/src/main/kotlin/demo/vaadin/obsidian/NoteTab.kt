package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.activeWhen
import flowik.vaadin.onAnimationEnd
import flowik.vaadin.onDomEvent
import flowik.vaadin.text
import java.util.function.Supplier

/**
 * A tab — file icon, live note name, close button — that grows into place
 * instead of appearing at full size, and shrinks away before it is closed.
 *
 * Both are CSS animations. Growing needs nothing from the server: a new tab
 * simply starts with it. Collapsing does, because the note must stay open until
 * the tab has visually gone — so [collapse] adds the `closing` class and waits
 * for the browser's `animationend` before reporting back.
 */
class NoteTab(
    val note: ObservableEntity<Note>,
    isActive: Supplier<Boolean>,
    onSelect: () -> Unit,
    onClose: () -> Unit
) : Div(), Bindings by Bindings() {

    /** Set while the tab is collapsing: what to run once it has. */
    private var onCollapsed: (() -> Unit)? = null

    init {
        addClassName("obs-tab")
        element.setAttribute("role", "tab")
        activeWhen(provider = isActive)

        add(
            VaadinIcon.FILE_TEXT.create(),
            Span().apply {
                addClassName("obs-tab-title")
                text { note[Note::name] }
            },
            IconButton(VaadinIcon.CLOSE, "Close tab", small = true, onClick = onClose)
                .apply { addClassName("obs-tab-close") }
        )

        // A click on the close button must not select the tab on its way out.
        onDomEvent("click", filter = "!event.target.closest('.obs-tab-close')") { onSelect() }

        onAnimationEnd("obs-tab-collapse") {
            val finished = onCollapsed ?: return@onAnimationEnd
            onCollapsed = null
            finished()
        }
    }

    /**
     * Shrinks the tab away and then runs [onFinished] — which is where the
     * caller actually closes the note, so the tab is gone before the model is.
     */
    fun collapse(onFinished: () -> Unit) {
        onCollapsed = onFinished
        addClassName("closing")
    }

    /**
     * Grows the tab back. Calling this during a [collapse] cancels it — the
     * pending close is dropped along with it, which is how a tab reopened
     * mid-collapse comes back.
     */
    fun expand() {
        onCollapsed = null
        removeClassName("closing")
    }
}

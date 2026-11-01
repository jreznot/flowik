package demo.vaadin.anki

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.vaadin.activeWhen
import flowik.vaadin.onClick
import flowik.vaadin.text
import flowik.vaadin.visible
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Supplier

private val TODAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

/**
 * The app bar: the logo, the Decks / Add / Browse sections, today's date and
 * the demo's "Next day" button.
 *
 * @param browsing    whether the Browse section is the one showing — Decks is
 *                    highlighted otherwise
 * @param daysShifted how far the demo clock runs ahead, shown next to the date
 */
class TopBar(
    today: Supplier<LocalDate>,
    daysShifted: Supplier<Int>,
    browsing: Supplier<Boolean>,
    onDecks: () -> Unit,
    onAddCards: () -> Unit,
    onBrowse: () -> Unit,
    onNextDay: () -> Unit
) : Div(), Bindings by Bindings() {

    init {
        addClassName("anki-top-bar")

        val logo = Span("flowik").apply {
            addClassName("anki-logo")
            add(Span(" anki").apply { addClassName("anki-logo-mark") })
        }

        val nav = Div(
            navItem("Decks", { !browsing.get() }, onDecks),
            navItem("Add", { false }, onAddCards),
            navItem("Browse", browsing, onBrowse)
        ).apply { addClassName("anki-nav") }

        val date = Span().apply {
            addClassName("anki-today")
            text { TODAY_FORMAT.format(today.get()) }
        }
        val shifted = Span().apply {
            addClassName("anki-shifted")
            text { "+${plural(daysShifted.get(), "day")}" }
            visible { daysShifted.get() > 0 }
        }
        val nextDay = NativeButton("Next day").apply {
            addClassName("anki-next-day")
            add(VaadinIcon.FORWARD.create())
            element.setAttribute("title", "Move the clock one day ahead, so tomorrow's reviews fall due")
            onClick(onNextDay)
        }

        add(logo, nav, Spacer(), date, shifted, nextDay)
    }

    private fun navItem(title: String, isActive: Supplier<Boolean>, onClick: () -> Unit) = NativeButton(title).apply {
        addClassName("anki-nav-item")
        activeWhen(provider = isActive)
        onClick(onClick)
    }
}

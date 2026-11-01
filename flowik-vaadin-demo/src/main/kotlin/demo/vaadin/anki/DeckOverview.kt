package demo.vaadin.anki

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.Button
import flowik.vaadin.shortcut
import flowik.vaadin.text
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * The screen between the deck list and studying: what is due today, and the
 * Study Now button — or, once the deck is done, why there is nothing left.
 *
 * It shows [deck] and its [queue] and reports the buttons; where Study Now or
 * Browse lead is up to the caller.
 */
class DeckOverview(
    deck: ObservableEntity<Deck>,
    queue: Supplier<DeckQueue>,
    onStudy: () -> Unit,
    onBack: () -> Unit,
    onAddCards: () -> Unit,
    onBrowse: () -> Unit,
    onOptions: () -> Unit
) : Div(), Bindings by Bindings() {

    init {
        addClassNames("anki-page", "anki-overview")

        val title = H2().apply { text { deck[Deck::name] } }
        val description = Caption(classNames = arrayOf("anki-muted")).apply {
            text { deck[Deck::description] }
            visible { deck[Deck::description].isNotBlank() }
        }

        val stats = Div(
            *Queue.entries.map { q ->
                Div(Caption(q.title + ":"), QueueCount(q) { queue.get().count(q) }).apply { addClassName("anki-stat") }
            }.toTypedArray()
        ).apply { addClassName("anki-stats") }

        val studyNow = Button("Study Now", onStudy).apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClassName("anki-study-now")
        }

        val due = Div(stats, studyNow).apply {
            addClassName("anki-due")
            visible { !queue.get().isFinished }
        }

        val finished = register(Congratulations(queue, onBack)).apply {
            visible { queue.get().isFinished }
        }

        val total = Caption(classNames = arrayOf("anki-muted")).apply {
            text { "${plural(queue.get().total, "card")} in this deck." }
        }

        val actions = Row(
            Button("Decks", onBack).apply { icon = VaadinIcon.ARROW_LEFT.create() },
            Spacer(),
            Button("Add", onAddCards),
            Button("Browse", onBrowse),
            Button("Options", onOptions)
        ).apply { addClassName("anki-actions") }

        add(title, description, due, finished, total, actions)

        // Anki's "S" — study the deck shown.
        shortcut(Key.KEY_S) { if (!queue.get().isFinished) onStudy() }
    }
}

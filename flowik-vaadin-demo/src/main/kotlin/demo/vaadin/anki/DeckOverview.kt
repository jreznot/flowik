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

/**
 * The screen between the deck list and studying: what is due today, and the
 * Study Now button — or, once the deck is done, why there is nothing left.
 */
class DeckOverview(
    private val store: AnkiStore,
    deck: ObservableEntity<Deck>,
    dialogs: AnkiDialogs
) : Div(), Bindings by Bindings() {

    init {
        addClassNames("anki-page", "anki-overview")
        val queue = { store.queueOf(deck) }

        val title = H2().apply { text { deck[Deck::name] } }
        val description = Caption(classNames = arrayOf("anki-muted")).apply {
            text { deck[Deck::description] }
            visible { deck[Deck::description].isNotBlank() }
        }

        val stats = Div(
            *Queue.entries.map { q ->
                Div(Caption(q.title + ":"), QueueCount(q) { queue().count(q) }).apply { addClassName("anki-stat") }
            }.toTypedArray()
        ).apply { addClassName("anki-stats") }

        val studyNow = Button("Study Now") { store.study(deck) }.apply {
            addThemeVariants(ButtonVariant.PRIMARY)
            addClassName("anki-study-now")
        }

        val due = Div(stats, studyNow).apply {
            addClassName("anki-due")
            visible { !queue().isFinished }
        }

        val finished = Congratulations(queue, onBack = store::showDecks).apply {
            visible { queue().isFinished }
        }
        register(finished)

        val total = Caption(classNames = arrayOf("anki-muted")).apply {
            text { "${plural(queue().total, "card")} in this deck." }
        }

        val actions = Row(
            Button("Decks") { store.showDecks() }.apply { icon = VaadinIcon.ARROW_LEFT.create() },
            Spacer(),
            Button("Add") { dialogs.addCards(deck) },
            Button("Browse") { store.browse(deck) },
            Button("Options") { dialogs.deckOptions(deck) }
        ).apply { addClassName("anki-actions") }

        add(title, description, due, finished, total, actions)

        // Anki's "S" — study the deck shown.
        shortcut(Key.KEY_S) { if (!queue().isFinished) store.study(deck) }
    }
}

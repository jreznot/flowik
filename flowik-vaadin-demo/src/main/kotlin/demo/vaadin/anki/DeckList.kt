package demo.vaadin.anki

import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.Button
import flowik.vaadin.items
import flowik.vaadin.text
import flowik.vaadin.visible
import java.util.function.Supplier

/**
 * Anki's main screen: every deck with its New / Learn / Due counts for today.
 *
 * The rows follow [AnkiStore.decks] one by one, and each count reads the
 * deck's derived queue — so answering a card anywhere re-counts the row with
 * nothing here listening for it.
 */
class DeckList(private val store: AnkiStore, dialogs: AnkiDialogs) : Div(), Bindings by Bindings() {

    init {
        addClassNames("anki-page", "anki-deck-list")

        val header = Div(
            Caption("Deck", "anki-col-name"),
            Caption("New", "anki-col-count"),
            Caption("Learn", "anki-col-count"),
            Caption("Due", "anki-col-count"),
            Caption("", "anki-col-action")
        ).apply { addClassNames("anki-table-row", "anki-table-header") }

        val rows = Div().apply {
            addClassName("anki-table-body")
            items(store.decks) { deck ->
                DeckRow(
                    deck,
                    queue = { store.queueOf(deck) },
                    onOpen = { store.showOverview(deck) },
                    onOptions = { dialogs.deckOptions(deck) }
                )
            }
        }

        val empty = Caption("No decks yet — create one to start adding cards.", "anki-placeholder").apply {
            visible { store.decks.isEmpty() }
        }

        val studied = Caption(classNames = arrayOf("anki-muted")).apply {
            text { "Studied ${plural(store.studiedToday, "card")} today." }
        }

        val actions = Row(
            Button("Create Deck") { dialogs.createDeck() },
            Button("Add Cards") { dialogs.addCards(null) }.apply { addThemeVariants(ButtonVariant.PRIMARY) }
        ).apply { addClassName("anki-actions") }

        add(H2("Decks"), Div(header, rows, empty).apply { addClassName("anki-table") }, studied, actions)
    }
}

/** One deck: its name, which opens the overview, today's counts and an options button. */
class DeckRow(
    deck: ObservableEntity<Deck>,
    queue: Supplier<DeckQueue>,
    onOpen: () -> Unit,
    onOptions: () -> Unit
) : Div(), Bindings by Bindings() {

    init {
        addClassNames("anki-table-row", "anki-deck-row")
        add(
            LinkButton(onClick = onOpen).apply {
                addClassName("anki-col-name")
                text { deck[Deck::name] }
            },
            *Queue.entries.map { q ->
                QueueCount(q) { queue.get().count(q) }.apply { addClassName("anki-col-count") }
            }.toTypedArray(),
            IconButton(VaadinIcon.COG, "Options", onOptions).apply { addClassName("anki-col-action") }
        )
    }
}

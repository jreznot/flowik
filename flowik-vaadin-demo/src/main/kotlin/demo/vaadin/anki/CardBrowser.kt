package demo.vaadin.anki

import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.value.ValueChangeMode
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.core.computed
import flowik.core.observable
import flowik.vaadin.Button
import flowik.vaadin.items
import flowik.vaadin.text
import flowik.vaadin.value
import flowik.vaadin.visible
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DUE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Anki's Browse window: every card of one deck, or of all of them, with its
 * scheduling state, searchable by either side. Clicking a row edits the card.
 *
 * The search text is local to the screen — an atom of its own, not store
 * state — and the matching cards are a computed over it and the store, shown
 * in a `Grid` through the `items` binding. Typing refilters them; answering,
 * forgetting or editing a card refreshes just its row. The computed lives no
 * longer than this screen: once its bindings are disposed it has no observers
 * left, and releases the store.
 */
class CardBrowser(
    private val store: AnkiStore,
    deck: ObservableEntity<Deck>?,
    dialogs: AnkiDialogs
) : Div(), Bindings by Bindings() {

    private val query = observable("", name = "browserQuery")

    private val shownCards = computed {
        val text = query.value.trim().lowercase()
        val source = if (deck == null) store.cards.items else store.cardsOf(deck)
        source.filter {
            text.isEmpty() || it[Card::front].lowercase().contains(text) || it[Card::back].lowercase().contains(text)
        }
    }

    init {
        addClassNames("anki-page", "anki-browser")

        val deckFilter = Select<ObservableEntity<Deck>>().apply {
            setAriaLabel("Deck")
            // The empty selection is "every deck" — and is labelled through here too.
            setItemLabelGenerator { it?.get(Deck::name) ?: "All decks" }
            isEmptySelectionAllowed = true
            emptySelectionCaption = "All decks"
            items { store.decks.items }
            value = deck
            // Another deck is another screen: the browser is rebuilt for it.
            addValueChangeListener { if (it.isFromClient) store.browse(it.value) }
        }

        val search = TextField().apply {
            placeholder = "Search front or back…"
            prefixComponent = VaadinIcon.SEARCH.create()
            isClearButtonVisible = true
            valueChangeMode = ValueChangeMode.EAGER
            value(query)
        }

        val count = Caption(classNames = arrayOf("anki-muted")).apply {
            text { plural(shownCards.value.size, "card") }
        }

        val toolbar = Row(
            deckFilter, search, Spacer(), count,
            Button("Add") { dialogs.addCards(deck) }.apply { addThemeVariants(ButtonVariant.PRIMARY) }
        ).apply {
            addClassName("anki-actions")
            addClassName("anki-actions-toolbar")
        }

        val grid = cardGrid(showDeck = deck == null, dialogs).apply {
            visible { shownCards.value.isNotEmpty() }
        }

        val empty = Caption("No cards match.", "anki-placeholder").apply {
            visible { shownCards.value.isEmpty() }
        }

        val back = Button("Decks") { store.showDecks() }.apply { icon = VaadinIcon.ARROW_LEFT.create() }

        add(
            H2().apply { text { deck?.let { "Browse — ${it[Deck::name]}" } ?: "Browse" } },
            toolbar,
            grid,
            empty,
            Row(back).apply { addClassName("anki-actions") }
        )
    }

    private fun cardGrid(showDeck: Boolean, dialogs: AnkiDialogs) = Grid<ObservableEntity<Card>>().apply {
        addClassName("anki-grid")
        isAllRowsVisible = true

        addColumn { it[Card::front] }.setHeader("Front").setFlexGrow(2)
        addColumn { it[Card::back] }.setHeader("Back").setFlexGrow(2)
        if (showDeck) addColumn { store.deckOf(it)?.get(Deck::name).orEmpty() }.setHeader("Deck")
        addColumn { it[Card::schedule].state.title }.setHeader("State")
            .setPartNameGenerator { "anki-state-${it[Card::schedule].state.name.lowercase()}" }
        addColumn { dueText(it[Card::schedule]) }.setHeader("Due")
        addColumn { card -> card[Card::schedule].intervalDays.takeIf { it > 0 }?.let(::formatDays) ?: "—" }
            .setHeader("Interval").setTextAlign(ColumnTextAlign.END).setFlexGrow(0).setWidth("96px")
        addColumn { card -> card[Card::schedule].ease.takeIf { it > 0 }?.let { "${(it * 100).toInt()}%" } ?: "—" }
            .setHeader("Ease").setTextAlign(ColumnTextAlign.END).setFlexGrow(0).setWidth("80px")
        val actions = addComponentColumn { card ->
            Row(
                IconButton(VaadinIcon.REFRESH, "Forget — start over as a new card") { store.forgetCard(card) },
                IconButton(VaadinIcon.TRASH, "Delete card") { store.deleteCard(card) }
            )
        }.setFlexGrow(0).setWidth("96px")

        // Clicks on a row edit the card, but not the ones on its buttons.
        addItemClickListener { event ->
            if (event.column !== actions) action { dialogs.editCard(event.item) }
        }

        // The Due column is relative to the clock, so the rows follow it too.
        items {
            store.now
            shownCards.value
        }
    }

    /** Learning cards are due in minutes, reviews on a day. */
    private fun dueText(schedule: Schedule): String {
        val now = store.now
        return when (schedule.state) {
            CardState.NEW -> "—"
            CardState.LEARNING, CardState.RELEARNING -> {
                val left = Duration.between(now, schedule.due)
                if (left.isNegative || left.isZero) "now" else "in ${formatDuration(left)}"
            }

            CardState.REVIEW -> when (val date = localDate(schedule.due, store.zone)) {
                store.today -> "today"
                store.today.plusDays(1) -> "tomorrow"
                else -> if (date < store.today) "${DUE_DATE.format(date)} (overdue)" else DUE_DATE.format(date)
            }
        }
    }
}

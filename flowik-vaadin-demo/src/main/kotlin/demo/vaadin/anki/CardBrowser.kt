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
import flowik.core.*
import flowik.vaadin.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.function.Supplier

private val DUE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Anki's Browse window: cards with their scheduling state, searchable by
 * either side, in a `Grid`. Clicking a row edits the card.
 *
 * The cards, the decks to filter by and the clock the Due column counts from
 * are handed in; picking another deck, editing, forgetting or deleting a card
 * is reported. The one piece of state it keeps is the search text: nothing
 * outside the browser reads it, so — as in Compose — it stays with the
 * component that uses it, and so does the filtered list derived from it. That
 * computed lives no longer than the screen: once its bindings are disposed it
 * has no observers left, and lets go of the state it read.
 *
 * @param deck     the deck being browsed, or `null` for all of them
 * @param cards    the cards to show before searching
 * @param deckName the deck a card belongs to, for the Deck column of "all decks"
 * @param now      the current time; the Due column is relative to it
 */
class CardBrowser(
    deck: ObservableEntity<Deck>?,
    decks: Supplier<List<ObservableEntity<Deck>>>,
    cards: Supplier<List<ObservableEntity<Card>>>,
    private val deckName: (ObservableEntity<Card>) -> String,
    private val now: Supplier<Instant>,
    private val zone: ZoneId,
    onSelectDeck: (ObservableEntity<Deck>?) -> Unit,
    onAddCards: () -> Unit,
    private val onEditCard: (ObservableEntity<Card>) -> Unit,
    private val onForgetCard: (ObservableEntity<Card>) -> Unit,
    private val onDeleteCard: (ObservableEntity<Card>) -> Unit,
    onBack: () -> Unit
) : Div(), Bindings by Bindings() {

    private val query = observable("", name = "browserQuery")

    private val shownCards = computed {
        val text = query.value.trim().lowercase()
        cards.get().filter {
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
            items(decks)
            value = deck
            addValueChangeListener { if (it.isFromClient) action { onSelectDeck(it.value) } }
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
            Button("Add", onAddCards).apply { addThemeVariants(ButtonVariant.PRIMARY) }
        ).apply { addClassName("anki-actions") }

        val grid = cardGrid(showDeck = deck == null).apply {
            visible { shownCards.value.isNotEmpty() }
        }

        val empty = Caption("No cards match.", "anki-placeholder").apply {
            visible { shownCards.value.isEmpty() }
        }

        val back = Button("Decks", onBack).apply { icon = VaadinIcon.ARROW_LEFT.create() }

        add(
            H2().apply { text { deck?.let { "Browse — ${it[Deck::name]}" } ?: "Browse" } },
            toolbar,
            grid,
            empty,
            Row(back).apply { addClassName("anki-actions") }
        )
    }

    private fun cardGrid(showDeck: Boolean) = Grid<ObservableEntity<Card>>().apply {
        addClassName("anki-grid")
        isAllRowsVisible = true

        addColumn { it[Card::front] }.setHeader("Front").setFlexGrow(2)
        addColumn { it[Card::back] }.setHeader("Back").setFlexGrow(2)
        if (showDeck) addColumn { deckName(it) }.setHeader("Deck")
        addColumn { it[Card::schedule].state.title }.setHeader("State")
            .setPartNameGenerator { "anki-state-${it[Card::schedule].state.name.lowercase()}" }
        addColumn { dueText(it[Card::schedule]) }.setHeader("Due")
        addColumn { card -> card[Card::schedule].intervalDays.takeIf { it > 0 }?.let(::formatDays) ?: "—" }
            .setHeader("Interval").setTextAlign(ColumnTextAlign.END).setFlexGrow(0).setWidth("96px")
        addColumn { card -> card[Card::schedule].ease.takeIf { it > 0 }?.let { "${(it * 100).toInt()}%" } ?: "—" }
            .setHeader("Ease").setTextAlign(ColumnTextAlign.END).setFlexGrow(0).setWidth("80px")
        val actions = addComponentColumn { card ->
            Row(
                IconButton(VaadinIcon.REFRESH, "Forget — start over as a new card") { onForgetCard(card) },
                IconButton(VaadinIcon.TRASH, "Delete card") { onDeleteCard(card) }
            )
        }.setFlexGrow(0).setWidth("96px")

        // Clicks on a row edit the card, but not the ones on its buttons.
        addItemClickListener { event ->
            if (event.column !== actions) action { onEditCard(event.item) }
        }

        // The Due column is relative to the clock, so the rows follow it too.
        items {
            now.get()
            shownCards.value
        }
    }

    /** Learning cards are due in minutes, reviews on a day. Runs while rendering, untracked. */
    private fun dueText(schedule: Schedule): String {
        val now = now.get()
        val today = localDate(now, zone)
        return when (schedule.state) {
            CardState.NEW -> "—"
            CardState.LEARNING, CardState.RELEARNING -> {
                val left = Duration.between(now, schedule.due)
                if (left.isNegative || left.isZero) "now" else "in ${formatDuration(left)}"
            }

            CardState.REVIEW -> when (val date = localDate(schedule.due, zone)) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> if (date < today) "${DUE_DATE.format(date)} (overdue)" else DUE_DATE.format(date)
            }
        }
    }
}

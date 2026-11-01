package demo.vaadin.anki

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.childSwitch
import flowik.vaadin.disposeOnDetach

/**
 * An Anki-style flashcard app: decks, the card browser, and the study loop
 * with Anki's SM-2 scheduling, all kept in memory by [AnkiStore].
 *
 * State is hoisted, as in Jetpack Compose. This is the only class that knows
 * [AnkiStore]; every component below it is stateless with respect to the app:
 * it gets the state it shows — as suppliers its bindings read, so it stays
 * reactive — and reports what the user does through `on…` callbacks, which
 * this class turns into store actions. A component can therefore be built in
 * a test from plain observables, or reused over any other state source.
 *
 * The page is a [TopBar] over one screen, chosen by [AnkiStore.screen]: the
 * content is a `childSwitch` over it, so navigating is writing that one atom
 * and the screen left behind is disposed with its bindings. The view is the
 * root of the UI's lifetime — the group is released when it is detached.
 */
@Route("anki")
@PageTitle("Flowik Anki")
@StyleSheet("anki.css")
class AnkiView(
    private val store: AnkiStore
) : Div(), Bindings by Bindings() {

    init {
        addClassName("anki")
        // A new view for every visit to the page, so this is when the data is (re)loaded.
        store.refresh()

        val topBar = register(
            TopBar(
                today = store::today,
                daysShifted = store::daysShifted,
                browsing = { store.screen is Screen.Browse },
                onDecks = store::showDecks,
                onAddCards = { openAddCards(currentDeck()) },
                onBrowse = { store.browse(currentDeck()) },
                onNextDay = store::skipDay
            )
        )

        val content = Div().apply {
            addClassName("anki-content")
            childSwitch(store::screen, ::screenFor)
        }

        add(topBar, content)
        disposeOnDetach()
    }

    private fun screenFor(screen: Screen): Component = when (screen) {
        Screen.Decks -> DeckList(
            decks = store.decks,
            countsOf = store::countsOf,
            studiedToday = store::studiedToday,
            onOpenDeck = store::showOverview,
            onDeckOptions = ::openDeckOptions,
            onCreateDeck = ::openCreateDeck,
            onAddCards = { openAddCards(null) }
        )

        is Screen.Overview -> DeckOverview(
            deck = screen.deck,
            queue = { store.queueOf(screen.deck) },
            onStudy = { store.study(screen.deck) },
            onBack = store::showDecks,
            onAddCards = { openAddCards(screen.deck) },
            onBrowse = { store.browse(screen.deck) },
            onOptions = { openDeckOptions(screen.deck) }
        )

        is Screen.Study -> StudyScreen(
            deck = screen.deck,
            queue = { store.queueOf(screen.deck) },
            resume = screen.resume,
            intervalLabels = store::intervalLabels,
            canUndo = store::canUndo,
            onAnswer = store::answer,
            onUndo = store::undo,
            onEdit = ::openEditCard,
            onForget = store::forgetCard,
            onDelete = store::deleteCard,
            onBack = { store.showOverview(screen.deck) },
            onDecks = store::showDecks
        )

        is Screen.Browse -> CardBrowser(
            deck = screen.deck,
            decks = { store.decks.items },
            cards = { screen.deck?.let(store::cardsOf) ?: store.cards.items },
            deckName = { card -> store.deckOf(card)?.get(Deck::name).orEmpty() },
            now = store::now,
            zone = store.zone,
            onSelectDeck = store::browse,
            onAddCards = { openAddCards(screen.deck) },
            onEditCard = ::openEditCard,
            onForgetCard = store::forgetCard,
            onDeleteCard = store::deleteCard,
            onBack = store::showDecks
        )
    }

    /** The deck the current screen is about, if any — where Add and Browse start. */
    private fun currentDeck(): ObservableEntity<Deck>? = when (val screen = store.screen) {
        Screen.Decks -> null
        is Screen.Overview -> screen.deck
        is Screen.Study -> screen.deck
        is Screen.Browse -> screen.deck
    }

    // Dialogs — opened here, so they are wired to the store the same way the screens are.

    private fun openAddCards(deck: ObservableEntity<Deck>?) {
        if (store.decks.isEmpty()) return openCreateDeck()
        AddCardsDialog(
            decks = { store.decks.items },
            initialDeck = deck ?: store.decks.items.first(),
            onAdd = { target, front, back -> store.addCard(target, front, back) }
        ).open()
    }

    private fun openEditCard(card: ObservableEntity<Card>) {
        EditCardDialog(
            card,
            deckName = { store.deckOf(card)?.get(Deck::name).orEmpty() },
            onSave = { front, back -> store.updateCard(card, front, back) }
        ).open()
    }

    private fun openDeckOptions(deck: ObservableEntity<Deck>) {
        DeckOptionsDialog(
            deck,
            cardCount = { store.countsOf(deck).total },
            isNameTaken = { store.isDeckNameTaken(it, except = deck) },
            onSave = { changes -> store.updateDeck(deck, changes) },
            onDelete = { store.deleteDeck(deck) }
        ).open()
    }

    private fun openCreateDeck() {
        CreateDeckDialog(isNameTaken = { store.isDeckNameTaken(it) }, onCreate = { store.createDeck(it) }).open()
    }
}

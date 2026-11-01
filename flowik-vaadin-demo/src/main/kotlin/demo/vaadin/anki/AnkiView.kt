package demo.vaadin.anki

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.NativeButton
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.vaadin.activeWhen
import flowik.vaadin.childSwitch
import flowik.vaadin.disposeOnDetach
import flowik.vaadin.onClick
import flowik.vaadin.text
import flowik.vaadin.visible
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TODAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

/**
 * An Anki-style flashcard app: decks, the card browser, and the study loop
 * with Anki's SM-2 scheduling, all kept in memory by [AnkiStore].
 *
 * The page is a top bar over one screen, chosen by [AnkiStore.screen]: the
 * content is a `childSwitch` over it, so navigating is writing that one atom
 * and the screen left behind is disposed with its bindings.
 *
 * The view is the root of the UI's lifetime — the group is released when it
 * is detached — and the owner of the dialogs the screens ask for.
 */
@Route("anki")
@PageTitle("Flowik Anki")
@StyleSheet("anki.css")
class AnkiView(private val store: AnkiStore) : Div(), Bindings by Bindings(), AnkiDialogs {

    init {
        addClassName("anki")
        store.tick()

        val content = Div().apply {
            addClassName("anki-content")
            childSwitch(store::screen, ::screenFor)
        }

        add(topBar(), content)
        disposeOnDetach()
    }

    private fun screenFor(screen: Screen): Component = when (screen) {
        Screen.Decks -> DeckList(store, this)
        is Screen.Overview -> DeckOverview(store, screen.deck, this)
        is Screen.Study -> StudyScreen(store, screen.deck, this)
        is Screen.Browse -> CardBrowser(store, screen.deck, this)
    }

    private fun topBar(): Div {
        val logo = Span("flowik").apply {
            addClassName("anki-logo")
            add(Span(" anki").apply { addClassName("anki-logo-mark") })
        }

        fun navItem(title: String, isActive: () -> Boolean, onClick: () -> Unit) = NativeButton(title).apply {
            addClassName("anki-nav-item")
            activeWhen { isActive() }
            onClick(onClick)
        }

        val nav = Div(
            navItem("Decks", { store.screen !is Screen.Browse }, store::showDecks),
            navItem("Add", { false }) { addCards(currentDeck()) },
            navItem("Browse", { store.screen is Screen.Browse }) { store.browse(currentDeck()) }
        ).apply { addClassName("anki-nav") }

        val today = Span().apply {
            addClassName("anki-today")
            text { TODAY_FORMAT.format(store.today) }
        }
        val shifted = Span().apply {
            addClassName("anki-shifted")
            text { "+${plural(store.daysShifted, "day")}" }
            visible { store.daysShifted > 0 }
        }
        val nextDay = NativeButton("Next day").apply {
            addClassName("anki-next-day")
            add(VaadinIcon.FORWARD.create())
            element.setAttribute("title", "Move the clock one day ahead, so tomorrow's reviews fall due")
            onClick { store.skipDay() }
        }

        return Div(logo, nav, Spacer(), today, shifted, nextDay).apply { addClassName("anki-top-bar") }
    }

    /** The deck the current screen is about, if any — where Add and Browse start. */
    private fun currentDeck(): ObservableEntity<Deck>? = when (val screen = store.screen) {
        Screen.Decks -> null
        is Screen.Overview -> screen.deck
        is Screen.Study -> screen.deck
        is Screen.Browse -> screen.deck
    }

    override fun addCards(deck: ObservableEntity<Deck>?) {
        if (store.decks.isEmpty()) createDeck() else AddCardsDialog(store, deck).open()
    }

    override fun editCard(card: ObservableEntity<Card>) {
        EditCardDialog(store, card).open()
    }

    override fun deckOptions(deck: ObservableEntity<Deck>) {
        DeckOptionsDialog(store, deck).open()
    }

    override fun createDeck() {
        CreateDeckDialog(isNameTaken = { store.isDeckNameTaken(it) }, onCreate = { store.createDeck(it) }).open()
    }
}

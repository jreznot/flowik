package flowik.vaadin

import com.vaadin.flow.component.contextmenu.MenuItem
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.select.Select
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.provider.DataChangeEvent
import flowik.core.Bindings
import flowik.core.ObservableEntity
import flowik.core.action
import flowik.core.autoRun
import flowik.core.observable
import flowik.core.observables
import flowik.vaadin.testing.simulateClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataBindingsTest {

    private data class Deck(val id: Int, val name: String)

    /** The items the grid's data provider is told to refresh from now on. */
    private fun <T> Grid<T>.refreshedItems(): List<T> = mutableListOf<T>().also { refreshed ->
        dataProvider.addDataProviderListener { event ->
            @Suppress("UNCHECKED_CAST")
            if (event is DataChangeEvent.DataRefreshEvent<*>) refreshed += event.item as T
        }
    }

    @Test
    fun `a select follows the list and keeps a selection that is still in it`() {
        val decks = observables(Deck(1, "Capitals"), Deck(2, "Spanish"))
        val select = Select<ObservableEntity<Deck>>()
        context(Bindings()) {
            select.setItemLabelGenerator { it[Deck::name] }
            select.items { decks.items }
        }
        select.value = decks[1]

        val added = action { decks.add(Deck(3, "Flowik")) }

        assertEquals(listOf("Capitals", "Spanish", "Flowik"), select.listDataView.items.toList().map { it[Deck::name] })
        assertSame(decks[1], select.value)

        action { decks.removeAt(1) }
        assertNull(select.value, "the selected deck left the list")
        assertEquals(2, select.listDataView.itemCount)
        select.value = added
    }

    @Test
    fun `a change inside an item refreshes just that item`() {
        val decks = observables(Deck(1, "Capitals"), Deck(2, "Spanish"))
        val grid = Grid<ObservableEntity<Deck>>()
        context(Bindings()) {
            grid.addColumn { it[Deck::name] }
            grid.items { decks.items }
        }
        val refreshed = grid.refreshedItems()

        action { decks[1][Deck::name] = "Español" }

        assertEquals(listOf(decks[1]), refreshed)
    }

    @Test
    fun `a grid keeps its selected rows across a replacement`() {
        val decks = observables(Deck(1, "A"), Deck(2, "B"), Deck(3, "C"))
        val query = observable("")
        val grid = Grid<ObservableEntity<Deck>>()
        context(Bindings()) {
            grid.items { decks.items.filter { query.value in it[Deck::name] } }
        }
        grid.select(decks[0])
        assertEquals(3, grid.listDataView.itemCount)

        action { decks.add(Deck(4, "AB")) }
        assertEquals(setOf(decks[0]), grid.selectedItems)

        action { query.value = "B" }
        assertEquals(listOf("B", "AB"), grid.listDataView.items.toList().map { it[Deck::name] })
        assertTrue(grid.selectedItems.isEmpty())
    }

    @Test
    fun `disposing the group stops following the list and the items`() {
        val decks = observables(Deck(1, "Capitals"))
        val grid = Grid<ObservableEntity<Deck>>()
        val bindings = Bindings()
        context(bindings) {
            grid.addColumn { it[Deck::name] }
            grid.items { decks.items }
        }
        val refreshed = grid.refreshedItems()

        bindings.dispose()
        action {
            decks[0][Deck::name] = "Renamed"
            decks.add(Deck(2, "Spanish"))
        }

        assertEquals(1, grid.listDataView.itemCount)
        assertTrue(refreshed.isEmpty())
    }

    @Test
    fun `errorMessage marks the field invalid while there is a message`() {
        val name = observable("")
        val field = TextField()
        context(Bindings()) {
            field.errorMessage { if (name.value.isBlank()) "A name is required." else null }
        }
        assertTrue(field.isInvalid)
        assertEquals("A name is required.", field.errorMessage)

        action { name.value = "Decks" }
        assertFalse(field.isInvalid)
        assertTrue(field.errorMessage.isNullOrEmpty())

        // The field validates itself on a value change; with no constraints of
        // its own it would call itself valid and undo what the binding decided.
        action { name.value = "" }
        field.value = "typed"
        assertTrue(field.isInvalid)
    }

    @Test
    fun `menu items run their handler as an action`() {
        val first = observable("a")
        val second = observable("b")
        var runs = 0
        autoRun { runs++; first.value + second.value }
        val menu = MenuBar()
        val item = menu.addItem("More").subMenu.item("Swap") {
            val a = first.value
            first.value = second.value
            second.value = a
        }

        item.simulateClick()

        assertEquals("b" to "a", first.value to second.value)
        assertEquals(2, runs, "both writes notify once")
    }

    @Test
    fun `a checkable menu item follows and flips its model`() {
        val showDone = observable(false)
        val item: MenuItem
        context(Bindings()) {
            item = MenuBar().addItem("View").subMenu.addItem("Show completed")
            item.checked(showDone)
        }
        assertTrue(item.isCheckable)
        assertFalse(item.isChecked)

        item.simulateClick()
        assertTrue(showDone.value)
        assertTrue(item.isChecked)

        action { showDone.value = false }
        assertFalse(item.isChecked)
    }

    @Test
    fun `a controlled checkable item that ignores the click stays as it was`() {
        val locked = observable(true)
        val item: MenuItem
        context(Bindings()) {
            item = MenuBar().addItem("View").subMenu.addItem("Locked")
            item.checked(locked) { /* not allowed to change */ }
        }

        item.simulateClick()

        assertTrue(locked.value)
        assertTrue(item.isChecked)
    }
}

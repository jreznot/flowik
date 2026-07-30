package flowik.core

import flowik.core.viewmodel.EntityViewModel
import flowik.core.viewmodel.StoreViewModel
import flowik.core.viewmodel.createViewModel
import kotlin.test.*

class ViewModelTest {

    private data class FormData(
        val name: String = "<Unnamed>",
        val email: String = "",
        val favoriteColor: String = ""
    )

    private data class Address(val city: String, val zip: String)

    private data class Team(val name: String, val address: Address, val tags: List<String> = emptyList())

    private data class Nickname(val value: String?)

    // Reading through to the model

    @Test
    fun `reads fall through to the model until the property is written`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        assertEquals("<Unnamed>", form[FormData::name])
        assertFalse(form.isDirty)
        assertFalse(form.isPropertyDirty(FormData::name))

        form[FormData::name] = "Pavan"

        assertEquals("Pavan", form[FormData::name])
        assertEquals("<Unnamed>", model[FormData::name], "the model is untouched until submit")
        assertTrue(form.isDirty)
        assertTrue(form.isPropertyDirty(FormData::name))
        assertFalse(form.isPropertyDirty(FormData::email), "a sibling property is unaffected")
    }

    @Test
    fun `a clean property follows the model, a dirty one does not`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        model[FormData::name] = "From the model"
        assertEquals("From the model", form[FormData::name])

        form[FormData::name] = "Edited"
        model[FormData::name] = "Changed again"
        assertEquals("Edited", form[FormData::name], "the buffer shadows the model")

        form.resetProperty(FormData::name)
        assertEquals("Changed again", form[FormData::name])
    }

    @Test
    fun `writing the value the model already has leaves the property clean`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::name] = "<Unnamed>"

        assertFalse(form.isDirty)
        assertEquals(emptyMap(), form.changedValues)
    }

    @Test
    fun `a dirty property stays dirty when the original value is written back`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::name] = "Pavan"
        form[FormData::name] = "<Unnamed>"

        assertTrue(form.isDirty, "as in mobx-utils — reverting an edit is what reset is for")
        assertEquals(mapOf("name" to "<Unnamed>"), form.changedValues)

        form.resetProperty(FormData::name)
        assertFalse(form.isDirty)
    }

    // Committing and reverting

    @Test
    fun `submit writes every buffered edit into the model`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::name] = "Pavan"
        form[FormData::email] = "pavan@pixelingene.com"
        form.submit()

        assertEquals("Pavan", model[FormData::name])
        assertEquals("pavan@pixelingene.com", model[FormData::email])
        assertFalse(form.isDirty)
        assertEquals(emptyMap(), form.changedValues)
    }

    @Test
    fun `reset discards every buffered edit`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::name] = "Pavan"
        form[FormData::email] = "pavan@pixelingene.com"
        form.reset()

        assertFalse(form.isDirty)
        assertEquals("<Unnamed>", form[FormData::name])
        assertEquals("", model[FormData::email])
    }

    @Test
    fun `resetProperty reverts one property and leaves the others`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::name] = "Pavan"
        form[FormData::email] = "pavan@pixelingene.com"
        form.resetProperty(FormData::name)

        assertEquals("<Unnamed>", form[FormData::name])
        assertEquals("pavan@pixelingene.com", form[FormData::email])
        assertTrue(form.isDirty)
        assertEquals(mapOf("email" to "pavan@pixelingene.com"), form.changedValues)
    }

    @Test
    fun `submit only writes the properties that were edited`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var emailWrites = 0
        model.property(FormData::email).subscribe { emailWrites++ }

        form[FormData::name] = "Pavan"
        form.submit()

        assertEquals(0, emailWrites)
    }

    @Test
    fun `rewriting the buffered value changes nothing`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var notifications = 0

        form[FormData::name] = "Pavan"
        form.subscribe { notifications++ }
        form[FormData::name] = "Pavan"

        assertEquals(0, notifications)
    }

    @Test
    fun `submit and reset on a clean view model do nothing`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var notifications = 0
        form.subscribe { notifications++ }

        form.submit()
        form.reset()
        form.resetProperty(FormData::name)

        assertEquals(0, notifications)
        assertFalse(form.isDirty)
    }

    @Test
    fun `changedValues lists the edits in the order they were made`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        form[FormData::favoriteColor] = "orange"
        form[FormData::name] = "Pavan"

        assertEquals(listOf("favoriteColor", "name"), form.changedValues.keys.toList())
        assertEquals(mapOf("favoriteColor" to "orange", "name" to "Pavan"), form.changedValues)
    }

    // Reactivity

    @Test
    fun `the mobx-utils example produces the documented transcript`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        val log = mutableListOf<String>()

        autoRun { log.add("${form[FormData::name]} / ${model[FormData::name]} / ${form.isDirty}") }

        form[FormData::name] = "Pavan"
        form.reset()
        form[FormData::name] = "Flowik"
        form.submit()

        assertEquals(
            listOf(
                "<Unnamed> / <Unnamed> / false",
                "Pavan / <Unnamed> / true",
                "<Unnamed> / <Unnamed> / false",
                "Flowik / <Unnamed> / true",
                "Flowik / Flowik / false"
            ),
            log
        )
    }

    @Test
    fun `reads are as fine-grained as the property`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var nameRuns = 0

        autoRun { form[FormData::name]; nameRuns++ }
        assertEquals(1, nameRuns)

        form[FormData::email] = "pavan@pixelingene.com"
        assertEquals(1, nameRuns, "an edit to a sibling property is not a change here")

        form[FormData::name] = "Pavan"
        assertEquals(2, nameRuns)
    }

    @Test
    fun `an edit is one change, however many properties it touches`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var runs = 0

        autoRun { form[FormData::name]; form[FormData::email]; form.isDirty; runs++ }
        assertEquals(1, runs)

        action {
            form[FormData::name] = "Pavan"
            form[FormData::email] = "pavan@pixelingene.com"
        }

        assertEquals(2, runs, "both edits coalesce into one re-run")
    }

    @Test
    fun `submit fires the model's dependents once`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        var runs = 0

        autoRun { model[FormData::name]; model[FormData::email]; runs++ }
        assertEquals(1, runs)

        form[FormData::name] = "Pavan"
        form[FormData::email] = "pavan@pixelingene.com"
        assertEquals(1, runs, "the model has not changed yet")

        form.submit()
        assertEquals(2, runs)
    }

    @Test
    fun `isDirty re-runs for a property touched for the first time`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        val states = mutableListOf<Boolean>()

        autoRun { states.add(form.isDirty) }
        form[FormData::email] = "pavan@pixelingene.com"
        form.reset()

        assertEquals(listOf(false, true, false), states)
    }

    @Test
    fun `subscribers are notified for edits and for model changes while clean`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        val name = form.property(FormData::name)
        var notifications = 0
        form.subscribe { notifications++ }

        model[FormData::name] = "From the model"
        assertEquals(1, notifications, "a clean property forwards the model")

        name.value = "Edited"
        assertEquals(2, notifications)

        model[FormData::name] = "Changed again"
        assertEquals(2, notifications, "the buffer shadows the model — nothing visible changed")

        form.submit()
        assertEquals(3, notifications)
    }

    // The buffered atom

    @Test
    fun `the buffered atom is a two-way binding target`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        val name: MutableObservable<String> = form.property(FormData::name)

        var seen = ""
        autoRun { seen = name.value }
        assertEquals("<Unnamed>", seen)

        name.value = "Pavan"

        assertEquals("Pavan", seen)
        assertEquals("Pavan", form[FormData::name])
        assertTrue(form.isPropertyDirty("name"))
    }

    @Test
    fun `the buffered atom works as a property delegate`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        class Draft(viewModel: EntityViewModel<FormData>) {
            var name: String by viewModel.property(FormData::name)
        }

        val draft = Draft(form)
        assertEquals("<Unnamed>", draft.name)

        draft.name = "Pavan"

        assertEquals("Pavan", form[FormData::name])
        assertEquals("<Unnamed>", model[FormData::name])
    }

    @Test
    fun `a property can be reset through its own atom`() {
        val model = observable(FormData())
        val form = createViewModel(model)
        val name = form.property(FormData::name)

        name.value = "Pavan"
        assertTrue(name.isDirty)

        name.reset()

        assertFalse(name.isDirty)
        assertFalse(form.isDirty)
        assertEquals("<Unnamed>", name.value)
    }

    @Test
    fun `the atom is created once and cached`() {
        val form = createViewModel(observable(FormData()))

        assertSame(form.property(FormData::name), form.get<String>("name"))
    }

    // Entity flavour specifics

    @Test
    fun `the model stays reachable through the view model`() {
        val model = observable(FormData())
        val form = createViewModel(model)

        assertSame(model, form.model)
    }

    @Test
    fun `an object property is buffered as a whole`() {
        val model = observable(Team("A-Team", Address("Munich", "80331")))
        val form = createViewModel(model)

        form[Team::address] = Address("Berlin", "10115")

        assertEquals(Address("Berlin", "10115"), form[Team::address])
        assertEquals(Address("Munich", "80331"), model[Team::address])

        form.submit()
        assertEquals(Address("Berlin", "10115"), model[Team::address])
    }

    @Test
    fun `a nested entity gets a view model of its own`() {
        val model = observable(Team("A-Team", Address("Munich", "80331")))
        val address = createViewModel(model.nested(Team::address))

        address[Address::city] = "Berlin"
        assertEquals("Munich", model.nested(Team::address)[Address::city])

        address.submit()
        assertEquals("Berlin", model.nested(Team::address)[Address::city])
    }

    @Test
    fun `a property already decomposed cannot also be buffered`() {
        val model = observable(Team("A-Team", Address("Munich", "80331")))
        model.nested(Team::address)
        val form = createViewModel(model)

        assertFailsWith<IllegalStateException> { form[Team::address] }
    }

    @Test
    fun `a nullable property buffers null as an edit`() {
        val model = observable(Nickname("Ally"))
        val form = createViewModel(model)

        form[Nickname::value] = null

        assertTrue(form.isDirty)
        assertNull(form[Nickname::value])
        assertEquals("Ally", model[Nickname::value])

        form.submit()
        assertNull(model[Nickname::value])
    }

    @Test
    fun `an unknown property is reported by name`() {
        val form = createViewModel(observable(FormData()))

        val failure = assertFailsWith<NoSuchElementException> { form.get<String>("nickname") }
        assertTrue("nickname" in failure.message!!, failure.message!!)
    }

    // Store flavour

    private class Settings {
        var host: String by observable("localhost")
        val port = observable(8080)
        var session: String by observableRef("anonymous")
        val url = computed { "http://localhost" }
        val tags = observableSet("core")
        val name = "settings"
    }

    @Test
    fun `an atom with its own change policy is buffered like any other`() {
        val settings = Settings()
        val form = createViewModel(settings)

        form[Settings::session] = "signed-in"

        assertEquals("signed-in", form[Settings::session])
        assertEquals("anonymous", settings.session)

        form.submit()
        assertEquals("signed-in", settings.session)
    }

    @Test
    fun `a delegated store property is buffered by property reference`() {
        val settings = Settings()
        val form = createViewModel(settings)

        assertEquals("localhost", form[Settings::host])

        form[Settings::host] = "example.org"

        assertEquals("example.org", form[Settings::host])
        assertEquals("localhost", settings.host)
        assertTrue(form.isDirty)

        form.submit()

        assertEquals("example.org", settings.host)
        assertFalse(form.isDirty)
    }

    @Test
    fun `a store property that holds its atom is buffered by the atom or by name`() {
        val settings = Settings()
        val form = createViewModel(settings)

        assertSame(form.property(settings.port), form.get<Int>("port"))

        form.property(settings.port).value = 9090

        assertEquals(9090, form.property(settings.port).value)
        assertEquals(8080, settings.port.value)
        assertTrue(form.isPropertyDirty("port"))

        form.submit()
        assertEquals(9090, settings.port.value)
    }

    @Test
    fun `the store itself stays reachable`() {
        val settings = Settings()

        assertSame(settings, createViewModel(settings).model)
    }

    @Test
    fun `only the writable atoms of a store are exposed`() {
        val form = createViewModel(Settings())

        assertEquals(setOf("host", "port", "session"), form.propertyNames)
    }

    @Test
    fun `a computed store property cannot be buffered`() {
        val form = createViewModel(Settings())

        val failure = assertFailsWith<IllegalArgumentException> { form.get<String>("url") }
        assertTrue("read-only" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a reactive collection cannot be buffered`() {
        val form = createViewModel(Settings())

        val failure = assertFailsWith<IllegalArgumentException> { form.get<Set<String>>("tags") }
        assertTrue("ObservableSet" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a plain property points at wrapping the model`() {
        val form = createViewModel(Settings())

        val failure = assertFailsWith<IllegalArgumentException> { form.get<String>("name") }
        assertTrue("createViewModel(observable(model))" in failure.message!!, failure.message!!)
    }

    @Test
    fun `an atom from somewhere else is rejected`() {
        val form = createViewModel(Settings())

        assertFailsWith<IllegalArgumentException> { form.property(observable("elsewhere")) }
    }

    @Test
    fun `an unknown store property is reported by name`() {
        val form = createViewModel(Settings())

        assertFailsWith<NoSuchElementException> { form.get<String>("timeout") }
    }

    @Test
    fun `a plain data class is wrapped rather than proxied`() {
        val form = createViewModel(observable(FormData()))

        assertIs<EntityViewModel<FormData>>(form)
        assertIs<StoreViewModel<FormData>>(createViewModel(FormData()))
    }
}

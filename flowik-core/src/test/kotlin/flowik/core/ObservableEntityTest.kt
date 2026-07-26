package flowik.core

import kotlin.test.*

class ObservableEntityTest {

    private data class Address(val city: String, val zip: String)

    private data class Member(val name: String, val active: Boolean = true)

    private data class Team(
        val name: String,
        val address: Address,
        val members: List<Member> = emptyList(),
        val tags: List<String> = emptyList(),
        val lead: Member? = null
    )

    private data class Company(val name: String, val team: Team)

    private fun team(): ObservableEntity<Team> = observable(
        Team(
            name = "A-Team",
            address = Address("Munich", "80331"),
            members = listOf(Member("Alice"), Member("Bob", active = false)),
            tags = listOf("core", "backend")
        )
    )

    // Shallow behaviour, unchanged

    @Test
    fun `scalar and list properties stay shallow`() {
        val team = team()

        assertEquals("A-Team", team[Team::name])
        assertEquals(listOf("core", "backend"), team[Team::tags])
        assertEquals("A-Team", team.get<String>("name").value)
    }

    @Test
    fun `a list property can be exposed as a shallow ObservableList`() {
        val team = team()

        assertEquals(listOf("core", "backend"), team.list<String>("tags").items)
    }

    @Test
    fun `an object property is atomic unless decomposed`() {
        val team = team()
        val address: Address = team[Team::address]

        assertEquals(Address("Munich", "80331"), address)
    }

    // Deep access — nested objects

    @Test
    fun `nested exposes an object's properties as independent atoms`() {
        val team = team()
        val address = team.nested(Team::address)

        var cityRuns = 0
        var zipRuns = 0
        autoRun { address[Address::city]; cityRuns++ }
        autoRun { address[Address::zip]; zipRuns++ }
        assertEquals(1, cityRuns)
        assertEquals(1, zipRuns)

        address[Address::city] = "Berlin"

        assertEquals(2, cityRuns)
        assertEquals(1, zipRuns, "the zip atom is untouched by a city write")
        assertEquals("Berlin", address[Address::city])
    }

    @Test
    fun `nested containers are created once and cached`() {
        val team = team()

        assertSame(team.nested(Team::address), team.nested(Team::address))
        assertSame(team.nested(Team::address), team.nested("address"))
        assertSame(team.property(Team::name), team.get<String>("name"))
    }

    @Test
    fun `the typed and the string accessor reach the same atom`() {
        val team = team()
        val address = team.nested(Team::address)

        address[Address::city] = "Berlin"

        assertEquals("Berlin", address.get<String>("city").value)
    }

    @Test
    fun `a nested walk reaches a shallow list two levels down`() {
        val company = observable(Company("Acme", Team("A-Team", Address("Munich", "80331"), tags = listOf("core"))))

        val tags: ObservableList<String> = company.nested(Company::team).list("tags")

        assertEquals(listOf("core"), tags.items)
    }

    @Test
    fun `a nested walk goes three levels down`() {
        val company = observable(Company("Acme", Team("A-Team", Address("Munich", "80331"))))

        val city: ObservableValue<String> = company.nested(Company::team).nested(Team::address).property(Address::city)
        city.value = "Berlin"

        assertEquals("Berlin", company.nested(Company::team).nested(Team::address)[Address::city])
    }

    @Test
    fun `nesting composes to arbitrary depth`() {
        val company = observable(Company("Acme", Team("A-Team", Address("Munich", "80331"))))
        val cities = mutableListOf<String>()

        autoRun { cities.add(company.nested(Company::team).nested(Team::address)[Address::city]) }
        company.nested(Company::team).nested(Team::address)[Address::city] = "Berlin"

        assertEquals(listOf("Munich", "Berlin"), cities)
    }

    // Deep access — lists of objects

    @Test
    fun `nestedList wraps elements so their properties are reactive`() {
        val team = team()
        val members = team.nestedList(Team::members)

        assertEquals(2, members.size)
        assertEquals("Alice", members[0][Member::name])

        var activeRuns = 0
        autoRun { members[0][Member::active]; activeRuns++ }

        members[0][Member::active] = false

        assertEquals(2, activeRuns)
        assertFalse(members[0][Member::active])
    }

    @Test
    fun `nestedList is list-reactive for its contents`() {
        val team = team()
        val members = team.nestedList(Team::members)
        val sizes = mutableListOf<Int>()

        autoRun { sizes.add(members.size) }
        members.add(Member("Carol"))
        members.remove(Member("Carol"))

        assertEquals(listOf(2, 3, 2), sizes)
    }

    @Test
    fun `an element property change does not invalidate readers of the list contents`() {
        val members = team().nestedList(Team::members)
        var runs = 0

        autoRun { members.items; runs++ }
        assertEquals(1, runs)

        members[0][Member::active] = false

        assertEquals(1, runs, "the contents did not change — only a property inside an element")
    }

    @Test
    fun `nested lists and maps can be reached through the same tree`() {
        val company = observable(
            Company("Acme", Team("A-Team", Address("Munich", "80331"), members = listOf(Member("Alice"))))
        )

        val team = company.nested(Company::team)
        team.nestedList(Team::members)[0][Member::name] = "Alicia"
        team.nested(Team::address)[Address::city] = "Berlin"

        assertEquals("Alicia", team.nestedList(Team::members)[0][Member::name])
        assertEquals("Berlin", company.nested(Company::team).nested(Team::address)[Address::city])
    }

    // Propagation to subscribers

    @Test
    fun `a change anywhere in the tree reaches the root subscriber`() {
        val company = observable(
            Company("Acme", Team("A-Team", Address("Munich", "80331"), members = listOf(Member("Alice"))))
        )
        val team = company.nested(Company::team)
        val members = team.nestedList(Team::members)

        var notifications = 0
        company.subscribe { notifications++ }

        company[Company::name] = "Acme Inc"
        assertEquals(1, notifications, "own property")

        team.nested(Team::address)[Address::city] = "Berlin"
        assertEquals(2, notifications, "two levels down")

        members[0][Member::name] = "Alicia"
        assertEquals(3, notifications, "inside a list element")

        members.add(Member("Bob"))
        assertEquals(4, notifications, "list contents")
    }

    @Test
    fun `a removed element stops notifying the list`() {
        val team = team()
        val members = team.nestedList(Team::members)
        var notifications = 0
        team.subscribe { notifications++ }

        val alice = members[0]
        members.removeAt(0)
        assertEquals(1, notifications, "the removal itself")

        alice[Member::name] = "Alicia"

        assertEquals(1, notifications, "the wrapper left the list and is no longer watched")
    }

    @Test
    fun `deep writes batch inside an action`() {
        val team = team()
        val address = team.nested(Team::address)
        var runs = 0

        autoRun {
            address[Address::city]
            address[Address::zip]
            runs++
        }
        assertEquals(1, runs)

        action {
            address[Address::city] = "Berlin"
            address[Address::zip] = "10115"
        }

        assertEquals(2, runs, "both writes coalesce into one re-run")
    }

    // Access-pattern consistency

    @Test
    fun `a property cannot be both an atom and a nested map`() {
        val team = team()
        team[Team::address]

        val failure = assertFailsWith<IllegalStateException> { team.nested(Team::address) }
        assertTrue("nested(\"address\")" in failure.message!!, failure.message!!)
        assertTrue("get(\"address\")" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a property cannot be both a nested map and an atom`() {
        val team = team()
        team.nested(Team::address)

        assertFailsWith<IllegalStateException> { team[Team::address] }
    }

    @Test
    fun `a list property cannot be both shallow and deep`() {
        val team = team()
        team.nestedList(Team::members)

        assertFailsWith<IllegalStateException> { team[Team::members] }
        assertFailsWith<IllegalStateException> { team.list<Member>("members") }
    }

    @Test
    fun `a shallow list stays shallow`() {
        val team = team()
        team.list<String>("tags")

        assertFailsWith<IllegalStateException> { team.nestedList<Member>("tags") }
    }

    // Error reporting

    @Test
    fun `decomposing a value rather than an object fails with a hint`() {
        val team = team()

        val failure = assertFailsWith<IllegalArgumentException> { team.nested<Address>("name") }
        assertTrue("get(\"name\")" in failure.message!!, failure.message!!)
    }

    @Test
    fun `decomposing a collection points at the list accessors`() {
        val team = team()

        val failure = assertFailsWith<IllegalArgumentException> { team.nested<Address>("tags") }
        assertTrue("nestedList(\"tags\")" in failure.message!!, failure.message!!)
    }

    @Test
    fun `decomposing a null property fails`() {
        val team = team()

        val failure = assertFailsWith<IllegalArgumentException> { team.nested<Member>("lead") }
        assertTrue("null" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a deep list of plain values fails with a hint`() {
        val team = team()

        val failure = assertFailsWith<IllegalArgumentException> { team.nestedList<Member>("tags") }
        assertTrue("list(\"tags\")" in failure.message!!, failure.message!!)
    }

    @Test
    fun `a deep list of plain values fails even when the list is empty`() {
        val team = observable(Team("A-Team", Address("Munich", "80331"), tags = emptyList()))

        val typed = assertFailsWith<IllegalArgumentException> { team.nestedList(Team::tags) }
        assertTrue("List<String>" in typed.message!!, typed.message!!)
        assertFailsWith<IllegalArgumentException> { team.nestedList<Member>("tags") }
    }

    @Test
    fun `a deep list needs a list property`() {
        val team = team()

        assertFailsWith<IllegalArgumentException> { team.nestedList<Member>("address") }
    }

    @Test
    fun `an unknown property is reported by name`() {
        val team = team()

        assertFailsWith<NoSuchElementException> { team.nested<Address>("headquarters") }
        assertFailsWith<NoSuchElementException> { team.nestedList<Member>("headquarters") }
        assertFailsWith<NoSuchElementException> { team.get<String>("headquarters") }
    }
}

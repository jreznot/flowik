package demo.vaadin.employees

import java.time.LocalDate

private val people = listOf(
    "Esther" to "Howard" to "Software Development Manager",
    "Jane" to "Cooper" to "Project Manager",
    "Kristin" to "Watson" to "Team Leader",
    "Cody" to "Fisher" to "Scrum Master",
    "Wade" to "Warren" to "Ethical Hacker",
    "Savannah" to "Nguyen" to "Software Developer",
    "Eleanor" to "Pena" to "Software Tester",
    "Arlene" to "McCoy" to "Ethical Hacker",
    "Albert" to "Flores" to "UI/UX Designer",
    "Ralph" to "Edwards" to "Software Tester",
    "Darlene" to "Robertson" to "Software Development Manager",
    "Kathryn" to "Murphy" to "Project Manager",
    "Annette" to "Black" to "Software Development Manager",
    "Theresa" to "Webb" to "Software Tester",
    "Cameron" to "Williamson" to "UI/UX Designer",
)

private val cities = listOf(
    Address("1234 Maple Street, Apartment 5A", "Anytown", "Meadowbrook County", "12345", "USA"),
    Address("88 Harbour Road", "Helsinki", "Uusimaa", "00100", "Finland"),
    Address("17 Linden Allee", "Berlin", "Berlin", "10115", "Germany"),
    Address("5 Rue des Fleurs", "Lyon", "Rhône", "69002", "France"),
)

private val diets = listOf(
    "Embracing a flexible approach to eating, with a focus on plant-based meals while occasionally " +
            "enjoying meat and seafood. No known allergies.",
    "Vegetarian.",
    "",
    "Lactose intolerant.",
)

private val relations = listOf("Spouse", "Parent", "Sibling", "Partner")

/** The fifteen people from the Vaadin docs page; Cody Fisher matches its screenshot. */
fun sampleEmployees(): List<Employee> = people.mapIndexed { index, (name, title) ->
    val (first, last) = name
    val id = index + 1
    val phone = "+358 12 345 ${6789 + (index - 3) * 37}"
    val email = "${first.lowercase()}${last.lowercase()}@abccompany.com"
    val (emergencyFirst, _) = people[(index + 5) % people.size].first

    Employee(
        id = id,
        firstName = first,
        lastName = last,
        title = title,
        department = if (title.contains("Designer")) "Product Design" else "Research and Development (R&D)",
        employmentType = if (index % 6 == 5) EmploymentType.CONTRACTOR else EmploymentType.FULL_TIME,
        startDate = LocalDate.of(2020, 7, 1).plusDays((3 - index) * 113L),
        birthDate = LocalDate.of(1975, 2, 12).plusDays((index - 3) * 911L),
        gender = if (index % 3 == 0) "Male" else "Female",
        timeZone = if (index % 2 == 0) "Europe/Helsinki" else "America/New_York",
        dietaryNotes = diets[(index + 1) % diets.size],
        address = cities[(index + 1) % cities.size],
        workPhone = phone,
        mobilePhone = phone,
        workEmail = email,
        slack = "@${first.lowercase()}.${last.lowercase()}",
        managerId = if (id == 1) null else 1 + index % 3,
        emergencyName = "$emergencyFirst $last",
        emergencyRelation = relations[index % relations.size],
        emergencyPhone = "+358 40 ${100 + index} ${4000 + index * 7}",
    )
}

fun newEmployee(id: Int): Employee = Employee(
    id = id,
    firstName = "New",
    lastName = "Employee",
    title = "",
    startDate = LocalDate.now(),
    birthDate = LocalDate.of(1990, 1, 1),
    gender = "",
    address = Address("", "", "", "", ""),
    workPhone = "",
    workEmail = "",
    slack = "",
)

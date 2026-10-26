package demo.vaadin.employees

import flowik.core.ObservableEntity
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Address(
    val street: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String,
)

enum class EmploymentType(val label: String) {
    FULL_TIME("Full-Time"),
    PART_TIME("Part-Time"),
    CONTRACTOR("Contractor"),
}

data class Employee(
    val id: Int,
    val firstName: String,
    val middleName: String = "",
    val lastName: String,
    val preferredName: String = firstName,
    val title: String,
    val department: String = "Research and Development (R&D)",
    val employmentType: EmploymentType = EmploymentType.FULL_TIME,
    val startDate: LocalDate,
    val birthDate: LocalDate,
    val gender: String,
    val timeZone: String = "Europe/Helsinki",
    val dietaryNotes: String = "",
    val address: Address,
    val workPhone: String,
    val mobilePhone: String = "",
    val homePhone: String = "",
    val workEmail: String,
    val slack: String,
    val managerId: Int? = null,
    val emergencyName: String = "",
    val emergencyRelation: String = "",
    val emergencyPhone: String = "",
)

// Reads below go through the entity, so a binding that calls them follows the
// properties it touched — an edit to the first name re-renders only the places
// that show it.

val ObservableEntity<Employee>.fullName: String
    get() = "${this[Employee::firstName]} ${this[Employee::lastName]}".trim()

val ObservableEntity<Employee>.initial: String
    get() = this[Employee::firstName].take(1).uppercase()

/** The avatar colour is tied to the person, not to their position in a list. */
val ObservableEntity<Employee>.colorIndex: Int
    get() = this[Employee::id] % 7

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun LocalDate.display(): String = format(dateFormat)

/** "3 years, 9 months and 14 days" */
fun tenure(since: LocalDate, today: LocalDate = LocalDate.now()): String {
    val period = Period.between(since, today)
    val parts = listOf(
        period.years to "year",
        period.months to "month",
        period.days to "day",
    ).filter { it.first > 0 }.map { (n, unit) -> "$n $unit${if (n == 1) "" else "s"}" }

    return when (parts.size) {
        0 -> "Starts today"
        1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }
}

fun localTime(zone: String): String =
    ZonedDateTime.now(ZoneId.of(zone)).format(timeFormat) + " local time"

/** "-" for a field that was left empty, as the detail page shows it. */
fun String.orDash(): String = ifBlank { "-" }

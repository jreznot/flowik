package demo.vaadin.employees

import com.vaadin.flow.component.HasElement
import com.vaadin.flow.component.avatar.Avatar
import com.vaadin.flow.component.avatar.AvatarVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Nav
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.router.RouterLayout
import com.vaadin.flow.router.RouterLink

/**
 * The router layout: the dark navigation bar on top, the routed page below.
 * The bar is plain Vaadin — `RouterLink` highlights the current page itself.
 */
class MainLayout : Div(), RouterLayout {
    private val content = Div().apply { addClassName("app-content") }

    init {
        addClassName("app-layout")

        val logo = Span("flowik").apply {
            addClassName("app-logo")
            add(Span("}>").apply { addClassName("app-logo-mark") })
        }

        val employees = RouterLink("Employees", EmployeesView::class.java).apply {
            // Also the root route, which the default prefix match would miss.
            setHighlightCondition { _, event ->
                event.location.path.let { it.isEmpty() || it.startsWith("employees") }
            }
        }
        val nav = Nav(
            employees,
            RouterLink("Teams", TeamsView::class.java),
            RouterLink("Locations", LocationsView::class.java),
        ).apply { addClassName("app-nav") }

        val user = Avatar("Flowik User").apply {
            abbreviation = "F"
            colorIndex = 4
            addThemeVariants(AvatarVariant.AURA_FILLED)
        }

        add(Div(logo, nav, Div().apply { addClassName("spacer") }, user).apply { addClassName("app-bar") }, content)
    }

    override fun showRouterLayoutContent(content: HasElement?) {
        content?.let { this.content.element.appendChild(it.element) }
    }
}

package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon

/**
 * The right sidebar. Its mentions section is static — this demo has no link
 * graph — and it carries a single live link in the middle. Wrap it in a
 * [SlidingPanel] to make it collapsible.
 *
 * Nothing in it is bound, so unlike its neighbours it owns no bindings.
 *
 * @param linkText caption of the centred link
 * @param href     where the link goes, opened in a new browser tab
 */
class BacklinksPanel(linkText: String, href: String) : Div() {

    init {
        addClassName("obs-backlinks")
        add(
            ToolIconRow(
                listOf(
                    ToolIcon(VaadinIcon.LINES_LIST, "Collapse results"),
                    ToolIcon(VaadinIcon.ARROWS_LONG_V, "Expand results"),
                    ToolIcon(VaadinIcon.SORT, "Change sort order"),
                    ToolIcon(VaadinIcon.SEARCH, "Search backlinks")
                )
            ).apply { addClassName("end") },
            mentions(),
            Div(ExternalLink(linkText, href)).apply { addClassName("obs-backlinks-link") }
        )
    }

    private fun mentions() = Div(
        Div(
            Caption("Linked mentions", "obs-mentions-title"),
            Caption("0")
        ).apply { addClassName("obs-mentions-header") },
        Caption("No backlinks found."),
        Caption("Unlinked mentions", "obs-mentions-title", "muted")
    ).apply { addClassName("obs-mentions") }
}

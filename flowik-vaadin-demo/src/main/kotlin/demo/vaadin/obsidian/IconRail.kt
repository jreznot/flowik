package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.vaadin.className

/**
 * The always-visible stripe of tool icons down the left edge.
 *
 * It is a sibling of the collapsible sidebar rather than a part of it, so
 * collapsing the sidebar never hides the rail.
 *
 * @param tools        selectable tools; clicking one writes its title to
 *                     [selectedTool] and the matching button is drawn active
 * @param selectedTool the currently chosen tool
 * @param footerTools  decorative icons pinned to the bottom
 * @param onSelect     runs after [selectedTool] is written — the view uses it
 *                     to re-open a collapsed sidebar
 */
class IconRail(
    tools: List<ToolIcon>,
    selectedTool: MutableObservable<String>,
    footerTools: List<ToolIcon> = emptyList(),
    onSelect: (ToolIcon) -> Unit = {}
) : Div(), Bindings by Bindings() {

    init {
        addClassName("obs-rail")

        tools.forEach { tool ->
            add(IconButton(tool.icon, tool.title) {
                selectedTool.value = tool.title
                onSelect(tool)
            }.apply {
                className("active") { selectedTool.value == tool.title }
            })
        }

        add(Div().apply { addClassName("obs-grow") })

        footerTools.forEach { tool -> add(IconButton(tool.icon, tool.title)) }
    }
}

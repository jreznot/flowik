package demo.swing.obsidian

import flowik.core.MutableObservable
import flowik.swing.BindingsPanel
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

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
 * @param onSelect     runs after [selectedTool] is written — the window uses it
 *                     to re-open a collapsed sidebar
 */
class IconRail(
    tools: List<ToolIcon>,
    selectedTool: MutableObservable<String>,
    footerTools: List<ToolIcon> = emptyList(),
    onSelect: (ToolIcon) -> Unit = {}
) : BindingsPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = BG_RAIL
        border = CompoundBorder(MatteBorder(0, 0, 0, 1, LINE), EmptyBorder(6, 4, 6, 4))

        tools.forEach { tool ->
            val button = IconButton(tool.ikon, tool.title) {
                selectedTool.value = tool.title
                onSelect(tool)
            }
            button.alignmentX = Component.CENTER_ALIGNMENT
            autoRun("rail.${tool.title}") { button.active = selectedTool.value == tool.title }
            add(button)
        }

        add(Box.createVerticalGlue())

        footerTools.forEach { tool ->
            add(IconButton(tool.ikon, tool.title).apply { alignmentX = Component.CENTER_ALIGNMENT })
        }
    }
}

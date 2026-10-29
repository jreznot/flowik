package demo.vaadin.obsidian

import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.MutableObservable
import flowik.core.toggle
import flowik.vaadin.className
import flowik.vaadin.text
import java.util.function.Supplier

/**
 * The window toolbar: decorative icon clusters on both sides, the current
 * document name in the middle, and the two sidebar toggles.
 *
 * @param title             text shown in the centre
 * @param leftPanelVisible  toggled by the left-hand switch, which is drawn
 *                          active while the panel is open
 * @param rightPanelVisible same, for the right-hand switch
 * @param leftIcons         decorative icons before the left switch
 * @param rightIcons        decorative icons before the right switch
 */
class TopBar(
    title: Supplier<String>,
    leftPanelVisible: MutableObservable<Boolean>,
    rightPanelVisible: MutableObservable<Boolean>,
    leftIcons: List<ToolIcon> = emptyList(),
    rightIcons: List<ToolIcon> = emptyList()
) : Div(), Bindings by Bindings() {

    init {
        addClassName("obs-top-bar")

        val toggleLeft = IconButton(VaadinIcon.SPLIT_H, "Toggle left sidebar") { leftPanelVisible.toggle() }
            .apply { className("active") { leftPanelVisible.value } }

        val toggleRight = IconButton(VaadinIcon.SPLIT_H, "Toggle right sidebar") { rightPanelVisible.toggle() }
            .apply { className("active") { rightPanelVisible.value } }

        val titleLabel = Caption(classNames = arrayOf("obs-top-title")).apply { text(title) }

        add(
            ToolIconRow(leftIcons, VerticalSeparator(), toggleLeft),
            titleLabel,
            ToolIconRow(rightIcons, VerticalSeparator(), toggleRight).apply { addClassName("end") }
        )
    }
}

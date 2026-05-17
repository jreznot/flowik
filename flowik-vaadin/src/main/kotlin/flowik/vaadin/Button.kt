package flowik.vaadin

import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.action
import flowik.core.autoRun
import java.util.function.Supplier
import com.vaadin.flow.component.button.Button as VButton

fun Button(label: String, onClick: () -> Unit): VButton =
    VButton(label) { action { onClick() } }

fun VButton.icon(provider: Supplier<VaadinIcon>) {
    autoRun("Button.icon") {
        icon = provider.get().create()
    }
}

package flowik.vaadin

import com.vaadin.flow.component.icon.VaadinIcon
import flowik.core.Bindings
import flowik.core.action
import java.util.function.Supplier
import com.vaadin.flow.component.button.Button as VButton

fun Button(label: String, onClick: () -> Unit): VButton =
    VButton(label) { action { onClick() } }

context(bindings: Bindings)
fun VButton.icon(provider: Supplier<VaadinIcon>) {
    bindings.autoRun("Button.icon") {
        icon = provider.get().create()
    }
}

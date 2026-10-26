package flowik.vaadin

import com.vaadin.flow.component.avatar.Avatar
import flowik.core.Bindings
import java.util.function.Supplier

context(bindings: Bindings)
fun Avatar.name(provider: Supplier<String?>) {
    bindings.autoRun("Avatar.name") {
        name = provider.get()
    }
}

context(bindings: Bindings)
fun Avatar.abbreviation(provider: Supplier<String?>) {
    bindings.autoRun("Avatar.abbreviation") {
        abbreviation = provider.get()
    }
}

context(bindings: Bindings)
fun Avatar.colorIndex(provider: Supplier<Int?>) {
    bindings.autoRun("Avatar.colorIndex") {
        colorIndex = provider.get()
    }
}

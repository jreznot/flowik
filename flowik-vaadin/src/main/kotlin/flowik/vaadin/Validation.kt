package flowik.vaadin

import com.vaadin.flow.component.HasValidation
import flowik.core.Bindings
import java.util.function.Supplier

/**
 * Shows the message [provider] returns as the field's error, and marks the
 * field invalid while there is one; `null` means valid.
 *
 * ```kotlin
 * name.errorMessage {
 *     if (store.isNameTaken(form[Deck::name])) "A deck with this name already exists." else null
 * }
 * ```
 *
 * It turns on the field's manual validation: otherwise the component runs its
 * own constraint check — on blur, for instance — and would overwrite the state
 * the binding set with "valid" whenever none of its constraints apply.
 */
context(bindings: Bindings)
fun HasValidation.errorMessage(provider: Supplier<String?>) {
    setManualValidation(true)
    bindings.autoRun("HasValidation.errorMessage") {
        val message = provider.get()
        errorMessage = message
        isInvalid = message != null
    }
}

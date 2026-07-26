package org.example

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import flowik.core.Store
import flowik.core.action
import flowik.core.computed
import flowik.core.observable
import flowik.intellij.FlowikBindings

/**
 * Live binding — a tool window / inspector panel. Every edit lands in the store
 * immediately, and anything derived from the store follows.
 */
fun contactPanel(parent: Disposable): DialogPanel {
    val store = object : Store {
        val name = observable("")
        val email = observable("")
        val subscribed = observable(false)

        val greeting = computed {
            if (name.value.isBlank()) "Hello, stranger" else "Hello, ${name.value}!"
        }
        val isValid = computed { name.value.isNotBlank() && "@" in email.value }

        fun reset() = action {
            name.value = ""
            email.value = ""
            subscribed.value = false
        }
    }

    return with(FlowikBindings(parent)) {
        panel {
            row("Name:") {
                textField().bindText(store.name)
            }
            row("Email:") {
                textField()
                    .bindText(store.email)
                    .enabledIf { store.name.value.isNotBlank() }
            }
            row {
                checkBox("Subscribe to the newsletter").bindSelected(store.subscribed)
            }
            row {
                label("").text { store.greeting.value }
            }
            row {
                button("Reset") { store.reset() }
            }.visibleIf { store.isValid.value }
        }
    }
}

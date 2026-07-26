package org.example

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import flowik.core.*
import flowik.intellij.*

/**
 * Live binding — a tool window / inspector panel. Every edit lands in the store
 * immediately, and anything derived from the store follows.
 */
fun ContactPanel(): DialogPanel {
    val store = object : Store {
        var name by observable("")
        var email by observable("")
        var subscribed by observable(false)

        val greeting by computed {
            if (name.isBlank()) "Hello, stranger" else "Hello, ${name}!"
        }
        val isValid by computed {
            name.isNotBlank() && "@" in email
        }

        fun reset() = action {
            name = ""
            email = ""
            subscribed = false
        }
    }

    return context(Bindings()) {
        panel {
            row("Name:") {
                textField().bindText(store::name)
            }
            row("Email:") {
                textField()
                    .bindText(store::email)
                    .enabledIf { store.name.isNotBlank() }
            }
            row {
                checkBox("Subscribe to the newsletter")
                    .bindSelected(store::subscribed)
            }
            row {
                label("").text(store::greeting)
            }
            row {
                button("Reset") { store.reset() }
            }.visibleIf { store.isValid }
        }
    }
}

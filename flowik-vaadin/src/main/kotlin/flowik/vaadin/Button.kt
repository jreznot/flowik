package flowik.vaadin

import flowik.core.action
import com.vaadin.flow.component.button.Button as VButton

fun Button(label: String, onClick: () -> Unit): VButton =
    VButton(label) { action { onClick() } }

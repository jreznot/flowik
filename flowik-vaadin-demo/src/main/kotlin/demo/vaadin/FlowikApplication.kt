package demo.vaadin

import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.page.AppShellConfigurator
import com.vaadin.flow.theme.aura.Aura
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlowikVaadinApplication

fun main(args: Array<String>) {
    runApplication<FlowikVaadinApplication>(*args)
}

@StyleSheet(Aura.STYLESHEET)
class Application : AppShellConfigurator
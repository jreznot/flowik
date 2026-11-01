package demo.anki

import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.page.AppShellConfigurator
import com.vaadin.flow.theme.aura.Aura
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AnkiApplication

fun main(args: Array<String>) {
    runApplication<AnkiApplication>(*args)
}

@Suppress("unused")
@StyleSheet(Aura.STYLESHEET)
class Application : AppShellConfigurator

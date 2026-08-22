plugins {
    id("org.jetbrains.intellij.platform.base")
}

dependencies {
    api(project(":flowik-core"))

    intellijPlatform {
        intellijIdea("2026.1.3")
    }
}

import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.2.20"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()

        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "flowik"

include("flowik-core")
include("flowik-swing")
include("flowik-swing-demo")
include("flowik-swing-async-demo")
include("flowik-vaadin")
include("flowik-vaadin-demo")
include("flowik-intellij-demo")

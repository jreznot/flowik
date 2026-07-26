plugins {
    kotlin("jvm") version "2.2.20"
}

val publishedModules = setOf("flowik-core", "flowik-swing", "flowik-vaadin", "flowik-intellij")

allprojects {
    group = "com.github.jreznot.flowik"
    version = (findProperty("version") as String?)?.takeUnless { it == "unspecified" }
        ?: "0.3.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    kotlin {
        jvmToolchain(21)
    }

    tasks.test {
        useJUnitPlatform()
    }

    if (name in publishedModules) {
        apply(plugin = "maven-publish")

        configure<JavaPluginExtension> {
            withSourcesJar()
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])

                    pom {
                        name.set(project.name)
                        description.set("Flowik — reactive UI bindings for Kotlin (${project.name}) inspired by MobX")
                        url.set("https://github.com/jreznot/flowik")

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }

                        scm {
                            url.set("https://github.com/jreznot/flowik")
                        }
                    }
                }
            }
        }
    }
}

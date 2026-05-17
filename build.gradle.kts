plugins {
    kotlin("jvm") version "2.3.20"
}

val publishedModules = setOf("flowik-core", "flowik-swing", "flowik-vaadin")

allprojects {
    group = "com.github.jreznot.flowik"
    version = (findProperty("version") as String?)?.takeUnless { it == "unspecified" }
        ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
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
                        description.set("Flowik — reactive UI bindings for Kotlin (${project.name})")
                        url.set("https://github.com/jreznot/flowik")

                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }

                        scm {
                            url.set("https://github.com/jreznot/flowik")
                            connection.set("scm:git:https://github.com/jreznot/flowik.git")
                            developerConnection.set("scm:git:ssh://git@github.com/jreznot/flowik.git")
                        }
                    }
                }
            }
        }
    }
}

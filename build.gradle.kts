plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "io.github.reaktor"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.5.4")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("demo.TodoAppKt")
}

tasks.test {
    useJUnitPlatform()
}

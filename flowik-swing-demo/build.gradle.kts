plugins {
    application
}

dependencies {
    implementation(project(":flowik-swing"))
    implementation("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.1")
    implementation("org.kordamp.ikonli:ikonli-swing:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-coreui-pack:12.4.0")
}

application {
    mainClass.set("demo.swing.TodoAppKt")
}

tasks.register<JavaExec>("runObsidian") {
    group = "application"
    description = "Runs the Obsidian-like notes demo"
    mainClass.set("demo.swing.ObsidianAppKt")
    classpath = sourceSets["main"].runtimeClasspath
}

plugins {
    application
}

dependencies {
    implementation(project(":flowik-swing"))
    implementation("com.formdev:flatlaf:3.7.1")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.1")
}

application {
    mainClass.set("demo.swing.TodoAppKt")
}

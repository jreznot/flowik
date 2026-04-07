plugins {
    application
}

dependencies {
    implementation(project(":flowik-swing"))
    implementation("com.formdev:flatlaf:3.5.4")
}

application {
    mainClass.set("demo.TodoAppKt")
}

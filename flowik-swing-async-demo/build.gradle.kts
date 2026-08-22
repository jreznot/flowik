plugins {
    application
}

dependencies {
    implementation(project(":flowik-swing"))
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

application {
    mainClass.set("demo.DataStoreAsyncDemoKt")
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val compileKotlin: KotlinCompile by tasks

compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-XXLanguage:+ContextParameters"))
}
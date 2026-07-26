dependencies {
    api(project(":flowik-core"))
    implementation("org.slf4j:slf4j-api:2.0.18")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

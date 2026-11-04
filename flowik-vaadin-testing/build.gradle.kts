// Test helpers for apps built on flowik-vaadin: testImplementation("…:flowik-vaadin-testing:…").
// A module of its own rather than java-test-fixtures, since JitPack rewrites Gradle module
// metadata and points every variant at the main jar, which breaks a test-fixtures variant.
plugins {
    id("io.spring.dependency-management") version "1.1.7"
}

extra["vaadinVersion"] = "25.3.3"

dependencies {
    api(project(":flowik-vaadin"))
}

dependencyManagement {
    imports {
        mavenBom("com.vaadin:vaadin-bom:${property("vaadinVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

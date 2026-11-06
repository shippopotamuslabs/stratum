plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    api(project(":core:infrastructure")) {
        // The fakes implement only infrastructure's own logging/telemetry
        // port types. Infrastructure's external dependencies are AAR-only
        // and cannot resolve on the JVM runtime classpaths of :core:testing's
        // JVM consumers (:core:domain / :core:application tests); Android
        // consumers receive them through their own infrastructure edge.
        isTransitive = false
    }
    implementation(libs.kotlinx.serialization.json)
    api(libs.junit.jupiter)
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

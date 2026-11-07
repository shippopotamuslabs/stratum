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
        // Accepted trade-off (STR-237): this api edge makes the WHOLE
        // :core:infrastructure main artifact visible on the TEST compile
        // classpaths of every :core:testing consumer, even though the fakes
        // use only infrastructure's 9 logging/telemetry port types.
        // Whole-module visibility is the accepted cost of faking those
        // ports; no consumer MAIN source set is exposed to infrastructure
        // through this edge — it reaches test classpaths only, via
        // consumers' testImplementation(project(":core:testing")).
        // Infrastructure's external dependencies are AAR-only and cannot
        // resolve on the JVM runtime classpaths of :core:testing's JVM
        // consumers (:core:domain / :core:application tests); Android
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

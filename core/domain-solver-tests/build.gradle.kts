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

dependencies {
    testImplementation(project(":core:domain"))
    testImplementation(project(":core:domain-solver"))
    testImplementation(libs.ortools.java)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Slow/synthesis tests are disabled by default during standard unit tests.
// Run them explicitly via: ./gradlew :core:domain-solver-tests:test -PrunSolverTests=true
val runSolverTests = project.hasProperty("runSolverTests")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    enabled = runSolverTests

    systemProperty("stratum.solver.weights", System.getProperty("stratum.solver.weights") ?: "")
    systemProperty("stratum.solver.deadline", System.getProperty("stratum.solver.deadline") ?: "")
    systemProperty("stratum.solver.log_search", System.getProperty("stratum.solver.log_search") ?: "")
    systemProperty("stratum.solver.warm_start", System.getProperty("stratum.solver.warm_start") ?: "")
    systemProperty("stratum.audit.run", System.getProperty("stratum.audit.run") ?: "")
}

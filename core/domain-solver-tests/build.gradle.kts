plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.stratum.domain.solver.tests"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:domain-solver"))
    implementation(libs.ortools.java)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Slow/synthesis tests are separated and disabled by default during standard unit tests.
// Run them explicitly via: ./gradlew :core:domain-solver-tests:testDebugUnitTest -PrunSolverTests=true
val runSolverTests = project.hasProperty("runSolverTests")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    enabled = runSolverTests

    systemProperty("stratum.solver.weights", System.getProperty("stratum.solver.weights") ?: "")
    systemProperty("stratum.solver.deadline", System.getProperty("stratum.solver.deadline") ?: "")
    systemProperty("stratum.audit.run", System.getProperty("stratum.audit.run") ?: "")
}

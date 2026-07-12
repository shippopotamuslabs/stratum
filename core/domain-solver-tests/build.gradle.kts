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
//
// Fast core matrix (default when enabled):
//   ./gradlew :core:domain-solver-tests:test -PrunSolverTests=true
// Full 28-scenario cartesian + multi-assert harnesses still use core by default via
//   stratum.solver.scenarios=core. Nightly / deep:
//   ./gradlew :core:domain-solver-tests:test -PrunSolverTests=true -PfullSolverScenarios=true
// Benchmark tuning suite (Optuna surface — ~56 solves):
//   ./gradlew :core:domain-solver-tests:test -PrunSolverTests=true -PincludeBenchmark=true \
//     --tests '*benchmarkTuningSuite*'
val runSolverTests = project.hasProperty("runSolverTests")
val fullSolverScenarios = project.hasProperty("fullSolverScenarios")
val includeBenchmark = project.hasProperty("includeBenchmark")
val includeSlow = project.hasProperty("includeSlow")

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        // Benchmark suite is Optuna/tuning only — not PR gate.
        if (!includeBenchmark) {
            excludeTags("benchmark")
        }
        // @Tag("slow") harnesses that still want full cartesian should pass -PfullSolverScenarios
        // and -PincludeSlow if tagged; core matrix tests are untagged.
        if (!includeSlow && !fullSolverScenarios) {
            excludeTags("slow")
        }
    }
    enabled = runSolverTests

    systemProperty("stratum.solver.weights", System.getProperty("stratum.solver.weights") ?: "")
    // Default short deadline for smoke; override with -Dstratum.solver.deadline=…
    systemProperty(
        "stratum.solver.deadline",
        System.getProperty("stratum.solver.deadline")
            ?: if (fullSolverScenarios) "30000" else "15000",
    )
    systemProperty("stratum.solver.log_search", System.getProperty("stratum.solver.log_search") ?: "")
    systemProperty("stratum.solver.warm_start", System.getProperty("stratum.solver.warm_start") ?: "")
    systemProperty("stratum.audit.run", System.getProperty("stratum.audit.run") ?: "")
    systemProperty(
        "stratum.solver.scenarios",
        System.getProperty("stratum.solver.scenarios")
            ?: if (fullSolverScenarios) "full" else "core",
    )
    // LocalSynthesisLab knobs (scripts/solver-local.sh lab …)
    systemProperty("stratum.lab.days", System.getProperty("stratum.lab.days") ?: "")
    systemProperty("stratum.lab.layout", System.getProperty("stratum.lab.layout") ?: "")
    systemProperty("stratum.lab.session_minutes", System.getProperty("stratum.lab.session_minutes") ?: "")
    systemProperty("stratum.lab.spec", System.getProperty("stratum.lab.spec") ?: "")

    // Surface println only when running the interactive lab (or when explicitly requested).
    val showStreams =
        System.getProperty("stratum.lab.days") != null ||
            System.getProperty("stratum.lab.show_streams")?.equals("true", ignoreCase = true) == true
    testLogging {
        showStandardStreams = showStreams
    }
}

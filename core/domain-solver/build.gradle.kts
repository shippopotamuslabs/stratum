plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.stratum.domain.solver"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 34
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
                arguments(
                    "-DBUILD_DEPS=ON",
                    "-DBUILD_CXX=ON",
                    "-DBUILD_JAVA=ON",
                    "-DBUILD_SAMPLES=OFF",
                    "-DBUILD_EXAMPLES=OFF",
                    "-DUSE_COINOR=OFF",
                    "-DUSE_SCIP=OFF",
                    "-DUSE_HIGHS=OFF",
                    "-DUSE_GLPK=OFF",
                )
                targets("jniortools")
            }
        }
    }

    ndkVersion = "28.2.13676358"

    // The CMake native build requires SWIG on the host and the OR-Tools source
    // checked out at third_party/or-tools. To save build times, we bypass the
    // CMake build if the prebuilt native libraries are already present in
    // jniLibs, unless explicitly forced via -PbuildOrToolsNatives.
    val hasPrebuilt = file("src/main/jniLibs/arm64-v8a/libortools.so").exists() &&
        file("src/main/jniLibs/arm64-v8a/libjniortools.so").exists()
    val forceBuildNatives = project.hasProperty("buildOrToolsNatives")
    val hasOrTools = file("src/main/cpp/CMakeLists.txt").exists() &&
        file("${rootDir}/third_party/or-tools/CMakeLists.txt").exists()

    if (hasOrTools && (!hasPrebuilt || forceBuildNatives)) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.28.3"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.ortools.java)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Slow-tagged tests (full combinatorial/property matrices) are excluded from the default test
// task so the iteration loop stays fast. Run them explicitly via `testSlow` or `-PincludeSlow`.
val includeSlow = project.hasProperty("includeSlow")

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        if (!includeSlow) {
            excludeTags("slow")
        }
    }
}

// Runs only the slow-tagged tests (nightly / CI). Usage: ./gradlew :core:domain-solver:testSlow
val testSlow by tasks.registering(Test::class) {
    useJUnitPlatform {
        includeTags("slow")
    }
    dependsOn("testDebugUnitTest")
}
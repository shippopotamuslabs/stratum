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
            }
        }
    }

    ndkVersion = "28.2.13676358"

    // The CMake native build requires SWIG on the host and the OR-Tools source
    // checked out at third_party/or-tools. Gate it behind a Gradle property so
    // the default build (CI, dev without SWIG) stays green. Run with:
    //   ./gradlew :core:domain-solver:assembleDebug -PbuildOrToolsNatives
    val buildNatives = project.hasProperty("buildOrToolsNatives")
    val hasOrTools = file("src/main/cpp/CMakeLists.txt").exists() &&
        file("${rootDir}/third_party/or-tools/CMakeLists.txt").exists()
    if (buildNatives && hasOrTools) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
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
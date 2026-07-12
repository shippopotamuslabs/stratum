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
    implementation(project(":core:domain"))
    // Full ortools-java with platform natives (linux/darwin/win). Used by host JVM tests and
    // Cloud Run — not wired into the Android app. Do not switch to :core:ortools-shaded here
    // (that strips natives for the old Android packaging path).
    //
    // Note: arm64 jniLibs under src/main/jniLibs and CMake under src/main/cpp are leftover from
    // the Android packaging era and are not consumed by this JVM module.
    implementation(libs.ortools.java)
}

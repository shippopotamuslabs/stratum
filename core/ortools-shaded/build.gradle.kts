import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    // java-base only — we publish a single shaded jar with no transitive deps.
    `java-base`
    alias(libs.plugins.shadow)
}

// Bundle OR-Tools Java + full protobuf-java, relocating com.google.protobuf so the Android
// app can also ship Firestore's protobuf-javalite (same package names otherwise →
// duplicate classes / WriteResponse VerifyError).

val shade: Configuration =
    configurations.create("shade") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

dependencies {
    // Platform native jars excluded: Android ships arm64 jniLibs in :core:domain-solver.
    shade(libs.ortools.java) {
        exclude(group = "com.google.ortools", module = "ortools-linux-x86-64")
        exclude(group = "com.google.ortools", module = "ortools-darwin-x86-64")
        exclude(group = "com.google.ortools", module = "ortools-win32-x86-64")
        exclude(group = "com.google.ortools", module = "ortools-linux-aarch64")
        exclude(group = "com.google.ortools", module = "ortools-darwin-aarch64")
    }
}

val shadowJar =
    tasks.register<ShadowJar>("shadowJar") {
        group = "build"
        description = "OR-Tools + protobuf-java with protobuf relocated"
        configurations = listOf(shade)
        archiveClassifier.set("")
        archiveBaseName.set("ortools-shaded")
        relocate("com.google.protobuf", "com.stratum.shaded.protobuf")
        mergeServiceFiles()
        // Avoid mergeDebugJavaResource clashes with Firestore/javalite .proto assets.
        exclude("google/protobuf/**")
        exclude("**/*.proto")
    }

// Replace default outgoing artifact with the fat jar only (no transitive protobuf-java).
configurations.named("default") {
    outgoing.artifacts.clear()
    outgoing.artifact(shadowJar)
}

tasks.named("assemble") {
    dependsOn(shadowJar)
}

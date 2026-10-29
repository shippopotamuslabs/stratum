import com.android.build.api.variant.HasUnitTestBuilder

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.apter.robolectric.junit5)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.stratum.core.ui"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// AGP 9.5.0-alpha06 validates Compose Preview prerequisites at configuration time and
// requires unit tests for every variant it packages preview runfiles for. Release unit
// tests resolve disabled by default, so opt back in (nothing executes them uninvoked).
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        // NOTE: call through HasUnitTestBuilder — the same accessors inherited via
        // VariantBuilder are synthetic in 9.5.0-alpha06 and invisible to Kotlin.
        (it as HasUnitTestBuilder).enableUnitTest = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    // Vico 3.x is built on JetBrains Compose Multiplatform, whose KMP facade artifacts
    // (org.jetbrains.compose.*) fight our AndroidX Compose BOM at runtime. We are a native
    // Android app, so the JetBrains Maven coordinates are stripped via module-scoped excludes;
    // Vico's bytecode then resolves cleanly against the BOM-managed androidx.compose.* deps.
    // (Note: vico has no separate `core` module in 3.x — everything lives in `compose`.)
    implementation(libs.vico.compose) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.animation")
    }
    implementation(libs.vico.compose.m3) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.material3")
    }
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.apter.junit5.robolectric.extension)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

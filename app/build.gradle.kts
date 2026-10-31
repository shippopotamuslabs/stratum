import java.util.Properties
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.screenshot)
}

// Release signing: gitignored root `keystore.properties` (see keystore.properties.example).
// Without it, release builds produce an *unsigned* AAB/APK that Play Console rejects.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }
}

// Derived versionCode (STR-205; the rules live in contracts/RELEASE.md):
// the code is the first-parent commit count of HEAD, read through the
// exec provider so it stays configuration-cache compatible. Nobody
// hand-bumps this number. A build with no usable git history (a source
// export, a failed git call, an unparsable count) falls back to the
// committed fallback below. Note a shallow checkout still answers git,
// with a count of 1: release builds must run from a full-history
// checkout (the CI jobs that build for distribution fetch with
// fetch-depth 0), and a code of 1 on a release build means the checkout
// was shallow, not that the fallback fired.
val fallbackVersionCode = 77
val derivedVersionCode: Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--first-parent", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toIntOrNull()
        ?.takeIf { it > 0 } ?: fallbackVersionCode
} catch (exception: Exception) {
    fallbackVersionCode
}

android {
    namespace = "com.stratum"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aether.stratum"
        minSdk = 34
        targetSdk = 37
        versionCode = derivedVersionCode
        versionName = "1.62.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // AGP 9: enables R8 code shrinking + optimization + resource shrinking.
            // Keep rules live in src/main/keepRules/*.keep (includes -dontobfuscate).
            optimization {
                enable = true
            }
            // Upload mapping.txt so Crashlytics can restore file names / line numbers after
            // shrinking and inlining, even with -dontobfuscate.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:infrastructure"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:program"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:subscription"))
    implementation(project(":feature:workout"))
    implementation(project(":feature:history"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.haze)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Release verification (contracts/RELEASE.md): prints the version a
// build from this checkout would carry, so a release prep can check the
// derived code against `git rev-list --first-parent --count HEAD`
// without assembling anything.
// `./gradlew :app:printVersion` -> "versionName=1.62.4 versionCode=439"
// A typed task with @Input properties: a doLast closure over script
// values does not survive this project's configuration cache.
abstract class PrintVersionTask : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun printVersion() {
        println("versionName=${versionName.get()} versionCode=${versionCode.get()}")
    }
}

tasks.register<PrintVersionTask>("printVersion") {
    group = "verification"
    description = "Prints the versionName and derived versionCode for this checkout."
    versionName.set(android.defaultConfig.versionName)
    versionCode.set(derivedVersionCode)
}

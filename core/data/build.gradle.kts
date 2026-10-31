plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apter.robolectric.junit5)
}

android {
    namespace = "com.stratum.core.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        // Kill-switch for Firestore cloud sync (client + WorkManager). Flip false for emergency.
        buildConfigField("boolean", "CLOUD_SYNC_ENABLED", "true")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("test") {
            assets.directories.addAll(
                listOf(
                    "$projectDir/schemas",
                    "$projectDir/src/main/assets",
                ),
            )
        }
    }
}

// Room schema export config — export schemas under core/data/schemas/ for golden migrations.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    // Transitional (STR-228 fold): the bottom layers (db, mapper) already live in
    // :core:infrastructure; this dependency dies with the module in the final chunk.
    implementation(project(":core:infrastructure"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.hilt.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Firestore requires protobuf-javalite + protolite. Do not exclude them (VerifyError on
    // WriteResponse).
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.billing)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.apter.junit5.robolectric.extension)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockk)
    testImplementation(project(":core:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
}

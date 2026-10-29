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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:application"))
    implementation(libs.kotlinx.serialization.json)
    api(libs.junit.jupiter)
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/**
 * Apply from the root build.gradle.kts after the Android Studio project exists:
 *
 *   plugins { id("io.gitlab.arturbosch.detekt") version "1.23.8" }
 *   apply(from = "gradle/detekt-convention.gradle.kts")
 *
 * Requires :detekt-rules in settings.gradle.kts:
 *   include(":detekt-rules")
 */

import io.gitlab.arturbosch.detekt.extensions.DetektExtension

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
    parallel = true
}

dependencies {
    add("detektPlugins", project(":detekt-rules"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

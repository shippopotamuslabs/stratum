plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.apter.robolectric.junit5) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.shadow) apply false
}

subprojects {
    // Pure JVM packaging / rules modules — skip Android-oriented detekt wiring.
    val excludedModules = setOf(":detekt-rules")
    if (path in excludedModules) {
        return@subprojects
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
    }

    dependencies {
        add("detektPlugins", rootProject.project(":detekt-rules"))
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }

    tasks.withType<Test>().configureEach {
        // Screenshot validation tasks run the plugin's generated JUnit4 tests — keep them on
        // the default JUnit4 runner (STR-11). Everything else stays on the JUnit Platform.
        if (!name.contains("ScreenshotTest")) {
            useJUnitPlatform()
        }
        // Profile-driven default (see build/reports/profile): the Robolectric
        // suites dominate build time and test classes are fork-isolated, so run
        // them in parallel. Driven by testMaxParallelForks (gradle.properties;
        // override per machine with -PtestMaxParallelForks=N or
        // ~/.gradle/gradle.properties), never by host topology here.
        maxParallelForks = providers.gradleProperty("testMaxParallelForks")
            .map { it.toInt().coerceAtLeast(1) }
            .getOrElse(1)
    }

    // NOTE: assemble intentionally does NOT depend on detekt. Static analysis is
    // gated by the detekt CI job (required pipeline) instead, so local builds
    // and CI assemble jobs stay fast. Run ./gradlew detekt explicitly.
    plugins.withId("org.jetbrains.kotlin.jvm") {
        val buildTypeAttr = Attribute.of("com.android.build.api.attributes.BuildTypeAttr", String::class.java)
        val artifactTypeAttr = Attribute.of("artifactType", String::class.java)
        configurations.all {
            if (isCanBeResolved) {
                attributes {
                    attribute(buildTypeAttr, "debug")
                    attribute(artifactTypeAttr, "jar")
                }
            }
        }
    }
}


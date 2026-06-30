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
}

subprojects {
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
        useJUnitPlatform()
    }

    plugins.withId("com.android.application") {
        tasks.matching { it.name.startsWith("assemble") }.configureEach {
            dependsOn(
                rootProject.subprojects
                    .filter { sub -> sub.path !in excludedModules && sub.tasks.findByName("detekt") != null }
                    .map { "${it.path}:detekt" },
            )
        }
    }

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


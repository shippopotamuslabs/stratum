pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // STR-298: Google's mirror of Maven Central, consulted before
        // Central itself. repo.maven.apache.org rate-limits the shared
        // egress IPs of hosted CI runners (HTTP 429 bursts that kill jobs
        // during dependency resolution); the mirror serves identical
        // content without that throttle, and Central stays as fallback.
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // STR-298: Maven Central mirror first (see pluginManagement above).
        maven {
            name = "MavenCentralMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
    }
}

rootProject.name = "Stratum"

include(":app")
include(":core:domain")
include(":core:application")
include(":core:ui")
include(":core:testing")
include(":core:infrastructure")
include(":detekt-rules")
include(":feature:onboarding")
include(":feature:program")
include(":feature:settings")
include(":feature:subscription")
include(":feature:workout")
include(":feature:history")
include(":solver-service")

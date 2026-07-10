pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
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
        mavenCentral()
    }
}

rootProject.name = "Stratum"

include(":app")
include(":core:domain")
include(":core:ortools-shaded")
include(":core:domain-solver")
include(":core:domain-solver-tests")
include(":core:data")
include(":core:ui")
include(":core:testing")
include(":core:inference")
include(":detekt-rules")
include(":feature:onboarding")
include(":feature:program")
include(":feature:settings")
include(":feature:workout")
include(":feature:history")

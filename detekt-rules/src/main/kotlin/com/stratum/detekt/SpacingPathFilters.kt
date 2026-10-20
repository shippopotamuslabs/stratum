package com.stratum.detekt

/**
 * Path scope for spacing-literal enforcement (STR-142).
 *
 * Unlike [StratumPathFilters] (feature/app only), spacing adoption covers every shipped
 * UI surface, including `:core:ui` components — so enforcement is every `src/main`
 * Kotlin file except:
 *
 * - `theme/` internals: shape/corner, border-width, and shadow geometry own raw dp by
 *   design (not spacing; see `StratumThemeSpec`, `NeoBrutalistSpec`).
 * - `*Preview.kt`, test, screenshotTest, and androidTest sources: tooling/fixture code.
 * - `:detekt-rules/` itself and build outputs.
 */
internal object SpacingPathFilters {
    private val EXEMPT_PATH_MARKERS =
        listOf(
            "/theme/",
            "/test/",
            "/screenshotTest",
            "/androidTest/",
            "/detekt-rules/",
            "/build/",
        )

    fun isEnforced(path: String): Boolean {
        if (path.endsWith("Preview.kt")) {
            return false
        }
        if (EXEMPT_PATH_MARKERS.any { marker -> path.contains(marker, ignoreCase = true) }) {
            return false
        }
        return path.contains("/src/main/", ignoreCase = true)
    }
}

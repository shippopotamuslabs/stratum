package com.stratum.detekt

internal object StratumPathFilters {
    private val EXEMPT_PATH_MARKERS =
        listOf(
            "/core/ui/theme/",
            "/core/ui/component/",
            "/detekt-rules/",
            "/test/",
            "/androidTest/",
        )

    private val ENFORCED_PATH_MARKERS =
        listOf(
            "/feature/",
            "/app/",
        )

    fun isExempt(path: String): Boolean =
        EXEMPT_PATH_MARKERS.any { marker -> path.contains(marker, ignoreCase = true) }

    fun isEnforced(path: String): Boolean {
        if (isExempt(path)) {
            return false
        }
        return ENFORCED_PATH_MARKERS.any { marker -> path.contains(marker, ignoreCase = true) }
    }
}

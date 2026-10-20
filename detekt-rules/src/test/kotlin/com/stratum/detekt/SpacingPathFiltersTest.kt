package com.stratum.detekt

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SpacingPathFiltersTest {

    @Test
    @DisplayName("feature main sources are enforced")
    fun featureMainEnforced() {
        assertTrue(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/feature/workout/src/main/kotlin/WorkoutScreen.kt",
            ),
        )
    }

    @Test
    @DisplayName("app main sources are enforced")
    fun appMainEnforced() {
        assertTrue(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/app/src/main/java/com/stratum/DashboardScreen.kt",
            ),
        )
    }

    @Test
    @DisplayName("core ui component sources are enforced")
    fun coreUiComponentEnforced() {
        assertTrue(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/core/ui/src/main/kotlin/component/StratumButton.kt",
            ),
        )
    }

    @Test
    @DisplayName("theme internals are exempt")
    fun themeExempt() {
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/core/ui/src/main/kotlin/theme/StratumThemeSpec.kt",
            ),
        )
    }

    @Test
    @DisplayName("preview files are exempt")
    fun previewExempt() {
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/feature/workout/src/main/kotlin/WorkoutScreenPreview.kt",
            ),
        )
    }

    @Test
    @DisplayName("test sources are exempt")
    fun testExempt() {
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/core/ui/src/test/kotlin/StratumShapesTest.kt",
            ),
        )
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/app/src/screenshotTest/kotlin/DashboardScreenshotTest.kt",
            ),
        )
    }

    @Test
    @DisplayName("detekt rules module itself is exempt")
    fun detektRulesExempt() {
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/detekt-rules/src/main/kotlin/ForbiddenRawSpacingLiteralInUi.kt",
            ),
        )
    }

    @Test
    @DisplayName("non-main sources are not enforced")
    fun nonMainNotEnforced() {
        assertFalse(
            SpacingPathFilters.isEnforced(
                "/home/user/Stratum/build/reports/detekt.xml",
            ),
        )
    }
}

package com.stratum.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Mirrors the enforcement style of the other `stratum` rule tests: lint real file
 * paths so [SpacingPathFilters] scope is exercised, not just the PSI matching.
 */
class ForbiddenRawSpacingLiteralInUiTest {

    @TempDir
    private lateinit var tempDir: Path

    private fun lintEnforced(relativePath: String, code: String) =
        ForbiddenRawSpacingLiteralInUi(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    @Test
    @DisplayName("raw dp in padding is reported in enforced paths")
    fun rawPaddingReported() {
        val findings =
            lintEnforced(
                "feature/workout/src/main/WorkoutScreen.kt",
                "fun x() { Modifier.padding(16.dp) }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("raw dp in spacedBy and PaddingValues is reported")
    fun rawSpacedByReported() {
        val findings =
            lintEnforced(
                "app/src/main/DashboardScreen.kt",
                "fun x() { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {} }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("raw dp as bare contentPadding value is reported")
    fun rawContentPaddingReported() {
        val findings =
            lintEnforced(
                "feature/history/src/main/HistoryScreen.kt",
                "fun x() { LazyColumn(contentPadding = 12.dp) {} }",
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("StratumSpacing token references pass")
    fun tokensPass() {
        val findings =
            lintEnforced(
                "feature/workout/src/main/WorkoutScreen.kt",
                "fun x() { Modifier.padding(StratumSpacing.s16) }",
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("zero dp passes")
    fun zeroPasses() {
        val findings =
            lintEnforced(
                "feature/workout/src/main/WorkoutScreen.kt",
                "fun x() { Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {} }",
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("component-spec sizes are not spacing positions")
    fun sizesPass() {
        val findings =
            lintEnforced(
                "core/ui/src/main/StratumButton.kt",
                "fun x() { Modifier.size(24.dp).height(1.dp); RoundedCornerShape(8.dp) }",
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("theme internals are exempt")
    fun themeExempt() {
        val findings =
            lintEnforced(
                "core/ui/src/main/theme/StratumThemeSpec.kt",
                "fun x() { Modifier.padding(20.dp) }",
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("previews are exempt")
    fun previewExempt() {
        val findings =
            lintEnforced(
                "feature/workout/src/main/WorkoutScreenPreview.kt",
                "fun x() { Modifier.padding(20.dp) }",
            )
        assertTrue(findings.isEmpty())
    }
}

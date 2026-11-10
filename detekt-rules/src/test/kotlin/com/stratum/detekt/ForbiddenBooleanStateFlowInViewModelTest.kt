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
 * Mirrors the enforcement style of the other `stratum` rule tests: lint real
 * file paths so [StratumPathFilters] scope is exercised, not just the PSI
 * matching.
 */
class ForbiddenBooleanStateFlowInViewModelTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lintFeature(relativePath: String, code: String) =
        ForbiddenBooleanStateFlowInViewModel(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    @Test
    @DisplayName("public StateFlow<Boolean> is reported with a section-4.4 citation")
    fun booleanStateFlowReported() {
        val findings =
            lintFeature(
                "feature/program/src/main/EditWorkoutViewModel.kt",
                """
                package com.stratum.feature.program

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class EditWorkoutViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(EditWorkoutUiState())
                    val uiState: StateFlow<EditWorkoutUiState> = _uiState.asStateFlow()

                    private val _isStarting = MutableStateFlow(false)
                    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("contracts/ARCHITECTURE.md §4.4"),
            "finding must cite the presentation contract: ${findings.single().message}",
        )
        assertTrue(
            findings.single().message.contains("`isStarting`"),
            "finding must name the fragment: ${findings.single().message}",
        )
    }

    @Test
    @DisplayName("non-Boolean StateFlow passes")
    fun nonBooleanStateFlowPasses() {
        val findings =
            lintFeature(
                "feature/settings/src/main/SettingsViewModel.kt",
                """
                package com.stratum.feature.settings

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class SettingsViewModel : ViewModel() {
                    private val _userMessage = MutableStateFlow<String?>(null)
                    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("nullable StateFlow<Boolean?> tri-state status reads are out of scope")
    fun nullableBooleanStateFlowPasses() {
        val findings =
            lintFeature(
                "app/src/main/OnboardingStatusViewModel.kt",
                """
                package com.stratum.navigation

                import kotlinx.coroutines.flow.StateFlow

                class OnboardingStatusViewModel : ViewModel() {
                    val isComplete: StateFlow<Boolean?> = observeComplete()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("private StateFlow<Boolean> transient locals pass")
    fun privateBooleanStateFlowPasses() {
        val findings =
            lintFeature(
                "app/src/main/DashboardViewModel.kt",
                """
                package com.stratum

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class DashboardViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(DashboardUiState())
                    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

                    private val _isRefreshing = MutableStateFlow(false)
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("non-ViewModel classes are not checked")
    fun nonViewModelClassNotChecked() {
        val findings =
            lintFeature(
                "feature/program/src/main/LoadingHelper.kt",
                """
                package com.stratum.feature.program

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class LoadingHelper {
                    private val _isLoaded = MutableStateFlow(false)
                    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }
}

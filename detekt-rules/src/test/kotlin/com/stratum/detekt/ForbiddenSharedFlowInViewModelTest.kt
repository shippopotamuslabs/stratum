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
class ForbiddenSharedFlowInViewModelTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lintFeature(relativePath: String, code: String) =
        ForbiddenSharedFlowInViewModel(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    @Test
    @DisplayName("public SharedFlow is reported with a section-4.2 citation")
    fun sharedFlowReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ExportLogsViewModel.kt",
                """
                package com.stratum.feature.settings

                import kotlinx.coroutines.flow.MutableSharedFlow
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.SharedFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asSharedFlow
                import kotlinx.coroutines.flow.asStateFlow

                class ExportLogsViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(ExportLogsUiState())
                    val uiState: StateFlow<ExportLogsUiState> = _uiState.asStateFlow()

                    private val _shareEvents = MutableSharedFlow<ExportFileResult>(extraBufferCapacity = 1)
                    val shareEvents: SharedFlow<ExportFileResult> = _shareEvents.asSharedFlow()
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("contracts/ARCHITECTURE.md §4.2"),
            "finding must cite the one-shot-events contract: ${findings.single().message}",
        )
        assertTrue(
            findings.single().message.contains("receiveAsFlow"),
            "finding must name the blessed shape: ${findings.single().message}",
        )
    }

    @Test
    @DisplayName("Channel-backed Flow events pass")
    fun channelBackedFlowPasses() {
        val findings =
            lintFeature(
                "feature/workout/src/main/WorkoutCompleteViewModel.kt",
                """
                package com.stratum.feature.workout

                import kotlinx.coroutines.channels.Channel
                import kotlinx.coroutines.flow.Flow
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow
                import kotlinx.coroutines.flow.receiveAsFlow

                class WorkoutCompleteViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(WorkoutCompleteUiState())
                    val uiState: StateFlow<WorkoutCompleteUiState> = _uiState.asStateFlow()

                    private val _events = Channel<WorkoutCompleteEvent>(Channel.BUFFERED)
                    val events: Flow<WorkoutCompleteEvent> = _events.receiveAsFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("private backing SharedFlow passes")
    fun privateSharedFlowPasses() {
        val findings =
            lintFeature(
                "app/src/main/DashboardViewModel.kt",
                """
                package com.stratum

                import kotlinx.coroutines.flow.MutableSharedFlow
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class DashboardViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(DashboardUiState())
                    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

                    private val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("StateFlow state is owned by the sibling rule, not this one")
    fun stateFlowNotReportedHere() {
        val findings =
            lintFeature(
                "feature/settings/src/main/SettingsViewModel.kt",
                """
                package com.stratum.feature.settings

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class SettingsViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(SettingsUiState())
                    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

                    private val _userMessage = MutableStateFlow<String?>(null)
                    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()
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
                "feature/settings/src/main/ShareHelper.kt",
                """
                package com.stratum.feature.settings

                import kotlinx.coroutines.flow.MutableSharedFlow
                import kotlinx.coroutines.flow.SharedFlow
                import kotlinx.coroutines.flow.asSharedFlow

                class ShareHelper {
                    private val _shareEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
                    val shareEvents: SharedFlow<String> = _shareEvents.asSharedFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }
}

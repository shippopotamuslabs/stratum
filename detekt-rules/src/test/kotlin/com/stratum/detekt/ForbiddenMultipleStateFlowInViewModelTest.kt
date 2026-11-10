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
class ForbiddenMultipleStateFlowInViewModelTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lintFeature(relativePath: String, code: String) =
        ForbiddenMultipleStateFlowInViewModel(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    @Test
    @DisplayName("second public StateFlow is reported with a section-4.4 citation")
    fun secondPublicStateFlowReported() {
        val findings =
            lintFeature(
                "feature/workout/src/main/WorkoutViewModel.kt",
                """
                package com.stratum.feature.workout

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class WorkoutViewModel : ViewModel() {
                    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()
                    private val _uiState = MutableStateFlow(WorkoutUiState.Loading)

                    private val _userMessage = MutableStateFlow<String?>(null)
                    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("contracts/ARCHITECTURE.md §4.4"),
            "finding must cite the presentation contract: ${findings.single().message}",
        )
        assertTrue(
            findings.single().message.contains("`uiState`") &&
                findings.single().message.contains("`userMessage`"),
            "finding must name every public StateFlow: ${findings.single().message}",
        )
    }

    @Test
    @DisplayName("single public StateFlow passes")
    fun singleStateFlowPasses() {
        val findings =
            lintFeature(
                "feature/program/src/main/ProgramsViewModel.kt",
                """
                package com.stratum.feature.program

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class ProgramsViewModel : ViewModel() {
                    private val _uiState = MutableStateFlow(ProgramsUiState())
                    val uiState: StateFlow<ProgramsUiState> = _uiState.asStateFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("Channel-backed Flow events are not state and are not counted")
    fun channelBackedFlowEventsNotCounted() {
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
    @DisplayName("private and internal StateFlows are transient locals, not collected state")
    fun nonPublicStateFlowsPass() {
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

                    private val detailStore = MutableStateFlow<GraphDetailStore?>(null)
                    internal val validationOverride = MutableStateFlow<String?>(null)
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
                "feature/workout/src/main/SessionHelper.kt",
                """
                package com.stratum.feature.workout

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class SessionHelper {
                    private val _a = MutableStateFlow(0)
                    val a: StateFlow<Int> = _a.asStateFlow()
                    private val _b = MutableStateFlow(0)
                    val b: StateFlow<Int> = _b.asStateFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }

    @Test
    @DisplayName("application-layer classes are out of scope")
    fun applicationLayerNotEnforced() {
        val findings =
            lintFeature(
                "core/application/src/main/ObserveThingUseCase.kt",
                """
                package com.stratum.application.workout

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                class ObserveThingViewModel {
                    private val _a = MutableStateFlow(0)
                    val a: StateFlow<Int> = _a.asStateFlow()
                    private val _b = MutableStateFlow(0)
                    val b: StateFlow<Int> = _b.asStateFlow()
                }
                """.trimIndent(),
            )
        assertTrue(findings.isEmpty())
    }
}

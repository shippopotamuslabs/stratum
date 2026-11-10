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

class ForbiddenPortInViewModelTest {
    @TempDir
    private lateinit var tempDir: Path

    private fun lintFeature(relativePath: String, code: String) =
        ForbiddenPortInViewModel(Config.empty).lint(write(relativePath, code))

    private fun write(relativePath: String, code: String): Path {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(code)
        return file
    }

    private fun viewModel(code: String): String = code

    @Test
    @DisplayName("AuthManager-shaped injection is reported with a section-4.4 citation")
    fun authManagerShapedInjectionReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ImportLogsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import com.stratum.application.auth.AuthManager
                    import javax.inject.Inject

                    class ImportLogsViewModel @Inject constructor(
                        private val authManager: AuthManager,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(1, findings.size)
        assertTrue(
            findings.single().message.contains("contracts/ARCHITECTURE.md §4.4"),
            "finding must cite the presentation contract: ${findings.single().message}",
        )
    }

    @Test
    @DisplayName("proposal-preferences-shaped injection is reported")
    fun preferencesShapedInjectionReported() {
        val findings =
            lintFeature(
                "feature/workout/src/main/WorkoutCompleteViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.workout

                    import com.stratum.application.workout.RepRangeShiftProposalPreferences
                    import javax.inject.Inject

                    class WorkoutCompleteViewModel @Inject constructor(
                        private val repRangeShiftProposalPreferences: RepRangeShiftProposalPreferences,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("legacy StarPort suffix injection is still reported")
    fun portSuffixInjectionReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ImportLogsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import javax.inject.Inject

                    class ImportLogsViewModel @Inject constructor(
                        private val logging: LoggingPort,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(1, findings.size)
    }

    @Test
    @DisplayName("use case injection is not reported")
    fun useCaseInjectionNotReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ImportLogsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import com.stratum.application.auth.GetAuthStateUseCase
                    import javax.inject.Inject

                    class ImportLogsViewModel @Inject constructor(
                        private val getAuthState: GetAuthStateUseCase,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("allowlisted WorkoutEditSession is not reported")
    fun workoutEditSessionAllowlisted() {
        val findings =
            lintFeature(
                "feature/program/src/main/LiftSwapViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.program

                    import com.stratum.domain.workout.WorkoutEditSession
                    import javax.inject.Inject

                    class LiftSwapViewModel @Inject constructor(
                        private val workoutEditSession: WorkoutEditSession,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("allowlisted EquipmentPickResult is not reported")
    fun equipmentPickResultAllowlisted() {
        val findings =
            lintFeature(
                "feature/program/src/main/EditWorkoutViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.program

                    import com.stratum.domain.lift.EquipmentPickResult
                    import javax.inject.Inject

                    class EditWorkoutViewModel @Inject constructor(
                        private val equipmentPickResult: EquipmentPickResult,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("allowlisted ForceRefreshDerivedTrainingData is not reported")
    fun forceRefreshDerivedTrainingDataAllowlisted() {
        val findings =
            lintFeature(
                "feature/settings/src/main/SettingsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import com.stratum.application.training.ForceRefreshDerivedTrainingData
                    import javax.inject.Inject

                    class SettingsViewModel @Inject constructor(
                        private val forceRefreshDerived: ForceRefreshDerivedTrainingData,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("feature-local boundary types are not reported")
    fun featureLocalBoundaryTypesNotReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ImportLogsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import com.stratum.feature.settings.AnalyticsRecorder
                    import com.stratum.feature.settings.DiagnosticDrain
                    import javax.inject.Inject

                    class ImportLogsViewModel @Inject constructor(
                        private val diagnosticDrain: DiagnosticDrain,
                        private val logAnalytics: AnalyticsRecorder,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("feature-local LiveWorkoutSurfaces is not reported")
    fun liveWorkoutSurfacesNotReported() {
        val findings =
            lintFeature(
                "feature/workout/src/main/WorkoutViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.workout

                    import com.stratum.feature.workout.notification.LiveWorkoutSurfaces
                    import javax.inject.Inject

                    class WorkoutViewModel @Inject constructor(
                        private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("infrastructure telemetry types are not reported")
    fun infrastructureTypesNotReported() {
        val findings =
            lintFeature(
                "feature/program/src/main/CreateProgramViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.program

                    import com.stratum.core.infrastructure.telemetry.TelemetryRouter
                    import javax.inject.Inject

                    class CreateProgramViewModel @Inject constructor(
                        private val telemetryRouter: TelemetryRouter,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("stdlib seam types are not reported")
    fun stdlibSeamTypesNotReported() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ExportLogsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.settings

                    import java.time.ZoneId
                    import javax.inject.Inject

                    class ExportLogsViewModel @Inject constructor(
                        private val zone: ZoneId,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("Repository injection is owned by the sibling rule, not this one")
    fun repositoryInjectionNotReportedHere() {
        val findings =
            lintFeature(
                "feature/program/src/main/ProgramsViewModel.kt",
                viewModel(
                    """
                    package com.stratum.feature.program

                    import com.stratum.domain.lift.LiftRepository
                    import javax.inject.Inject

                    class ProgramsViewModel @Inject constructor(
                        private val liftRepository: LiftRepository,
                    )
                    """.trimIndent(),
                ),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("non-ViewModel classes are not checked")
    fun nonViewModelClassNotChecked() {
        val findings =
            lintFeature(
                "feature/settings/src/main/ImportHelper.kt",
                """
                package com.stratum.feature.settings

                import com.stratum.application.auth.AuthManager
                import javax.inject.Inject

                class ImportHelper @Inject constructor(
                    private val authManager: AuthManager,
                )
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    @DisplayName("application-layer use cases wiring AuthManager stay out of scope")
    fun applicationLayerNotEnforced() {
        val findings =
            lintFeature(
                "core/application/src/main/AuthUseCases.kt",
                """
                package com.stratum.application.auth

                import javax.inject.Inject

                class AuthViewModel @Inject constructor(
                    private val authManager: AuthManager,
                )
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }
}

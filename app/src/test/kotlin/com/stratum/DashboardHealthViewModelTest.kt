package com.stratum

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectMetricsPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.app.DiagnosticDrain
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.application.dashboard.LoadDashboardUseCase
import com.stratum.application.dashboard.LoadGraphDetailUseCase
import com.stratum.application.dashboard.ObserveDashboardPreferencesUseCase
import com.stratum.application.dashboard.ObserveDashboardStateUseCase
import com.stratum.application.dashboard.SetDashboardBoardTabUseCase
import com.stratum.domain.health.DailyStepCount
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.health.HealthMetricType
import com.stratum.domain.health.HealthMetricsSnapshot
import com.stratum.application.healthconnect.LoadHealthConnectMetricsUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.domain.health.WeightSample
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.application.sync.ObserveCloudSyncStatusUseCase
import com.stratum.application.sync.RequestCloudSyncUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class DashboardHealthViewModelTest {

    private val testZone: ZoneId = ZoneOffset.UTC

    @Test
    @DisplayName("health pager stays hidden when Health Connect is off")
    fun healthPagerHiddenWhenDisabled() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.health.visible)
        assertEquals(DashboardBoardUiState.Empty, viewModel.uiState.value.board)
    }

    @Test
    @DisplayName("health pager stays hidden when opted in but no reads are granted")
    fun healthPagerHiddenWhenNoReads() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = emptySet()
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.health.visible)
    }

    @Test
    @DisplayName("health pager is visible on an empty board when reads are granted")
    fun healthPagerVisibleOnEmptyBoard() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Steps)
        val today = LocalDate.of(2026, 8, 12)
        val now = today.atTime(15, 0).toInstant(ZoneOffset.UTC)
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(
                DailyStepCount(today.atStartOfDay().toInstant(ZoneOffset.UTC), 8_432L),
            ),
        )
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
            now = now,
        )
        advanceUntilIdle()

        assertEquals(DashboardBoardUiState.Empty, viewModel.uiState.value.board)
        val health = viewModel.uiState.value.health
        assertEquals(true, health.visible)
        assertEquals("8,432", health.activity.todayStepsText)
    }

    @Test
    @DisplayName("activity page uses empty copy when steps are granted but the window is all zeros")
    fun healthActivityEmptyWhenNoStepRecords() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Steps)
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
        )
        advanceUntilIdle()

        val health = viewModel.uiState.value.health
        assertEquals(true, health.visible)
        assertEquals(null, health.activity.todayStepsText)
    }

    @Test
    @DisplayName("weight page follows the display unit preference")
    fun healthWeightFollowsDisplayUnit() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Weight)
        val today = LocalDate.of(2026, 8, 12)
        val now = today.atTime(15, 0).toInstant(ZoneOffset.UTC)
        metrics.snapshot = HealthMetricsSnapshot(
            weightSamples = listOf(
                WeightSample(
                    time = today.atTime(7, 0).toInstant(ZoneOffset.UTC),
                    weight = Weight(80.0, Weight.WeightUnit.Kg),
                ),
            ),
        )
        val weightPrefs = FakeWeightDisplayPreferencesRepository()
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
            weightDisplayPreferences = weightPrefs,
            now = now,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.health.weight.latestText?.contains("lb") == true)

        weightPrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))
        advanceUntilIdle()
        assertEquals("80.0 kg", viewModel.uiState.value.health.weight.latestText)
    }

    @Test
    @DisplayName("in-flight health load does not republish the pager after opt-out")
    fun inFlightLoadDoesNotRepublishAfterDisable() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Steps)
        val today = LocalDate.of(2026, 8, 12)
        val now = today.atTime(15, 0).toInstant(ZoneOffset.UTC)
        val gate = CompletableDeferred<Unit>()
        metrics.snapshotGate = gate
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(
                DailyStepCount(today.atStartOfDay().toInstant(ZoneOffset.UTC), 8_432L),
            ),
        )
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
            now = now,
        )
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.health.visible)

        healthPrefs.setPreferences(HealthConnectPreferences(enabled = false))
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.health.visible)
    }

    @Test
    @DisplayName("pull-to-refresh reloads Health Connect pages and clears the refreshing flag")
    fun refreshReloadsHealthPages() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Steps)
        val today = LocalDate.of(2026, 8, 12)
        val now = today.atTime(15, 0).toInstant(ZoneOffset.UTC)
        val todayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC)
        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(DailyStepCount(todayStart, 1_000L)),
        )
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
            now = now,
        )
        advanceUntilIdle()
        assertEquals("1,000", viewModel.uiState.value.health.activity.todayStepsText)
        val readsAfterLoad = metrics.readSnapshotCount

        metrics.snapshot = HealthMetricsSnapshot(
            dailySteps = listOf(DailyStepCount(todayStart, 9_999L)),
        )
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("9,999", viewModel.uiState.value.health.activity.todayStepsText)
        assertTrue(metrics.readSnapshotCount > readsAfterLoad)
        assertEquals(false, viewModel.uiState.value.isRefreshing)
    }

    @Test
    @DisplayName("STR-237 AQ1 + STR-240: read failure logs exactly once at the drain and publishes unavailable")
    fun readFailureLogsOnceAtDashboardDrain() = runTest {
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))
        val metrics = FakeHealthConnectMetricsPort()
        metrics.granted = setOf(HealthMetricType.Steps)
        val cause = IllegalStateException("sdk blip")
        metrics.readSnapshotFailure = cause
        val drainLogging = FakeLoggingPort()
        val viewModel = buildViewModel(
            healthConnectPreferences = healthPrefs,
            healthConnectMetrics = metrics,
            drainLogging = drainLogging,
        )
        advanceUntilIdle()
        // Init settles its own loads; one explicit load must log exactly once, at the drain.
        drainLogging.errors.clear()
        viewModel.load()
        advanceUntilIdle()

        // AQ1 ruling (2026-11-06): the use case records nothing; the single error
        // is the dashboard drain record carrying the real cause from the diagnostic.
        assertEquals(1, drainLogging.errors.size)
        val logged = drainLogging.errors.single()
        assertEquals("HealthConnectMetrics", logged.tag)
        assertEquals(
            "Failed to read Health Connect snapshot — dashboard shows metrics unavailable",
            logged.message,
        )
        assertSame(cause, logged.throwable)
        // STR-240 supersedes the AQ1-era "empty pages still publish" pin: the degraded
        // pages publish as unavailable (visible, no chart, no page data), and the
        // diagnostic itself still never reaches UI state.
        val health = viewModel.uiState.value.health
        assertEquals(true, health.visible)
        assertEquals(true, health.unavailable)
        assertEquals(false, health.hasChart())
        assertEquals(null, health.activity.todayStepsText)
        assertTrue(health.activity.dailyPoints.isEmpty())
    }

    private fun buildViewModel(
        healthConnectPreferences: FakeHealthConnectPreferencesRepository =
            FakeHealthConnectPreferencesRepository(),
        healthConnectMetrics: FakeHealthConnectMetricsPort = FakeHealthConnectMetricsPort(),
        weightDisplayPreferences: FakeWeightDisplayPreferencesRepository =
            FakeWeightDisplayPreferencesRepository(),
        drainLogging: FakeLoggingPort = FakeLoggingPort(),
        cloudSync: FakeCloudSyncPort = FakeCloudSyncPort(),
        dashboardPreferences: FakeDashboardPreferencesRepository =
            FakeDashboardPreferencesRepository(),
        now: Instant? = null,
    ): DashboardViewModel {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val observeDashboardState = ObserveDashboardStateUseCase(
            loadDashboard = LoadDashboardUseCase(
                muscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
                liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
                muscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
                liftRepository = FakeLiftRepository(),
                trainingLogRepository = FakeTrainingLogRepository(),
                programRepository = FakeProgramRepository(program),
            ),
            loadHealthConnectMetrics = LoadHealthConnectMetricsUseCase(
                healthConnectMetrics,
                healthConnectPreferences,
            ),
            observeDashboardPreferences =
                ObserveDashboardPreferencesUseCase(dashboardPreferences),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(weightDisplayPreferences),
            observeHealthConnectPreferences =
                ObserveHealthConnectPreferencesUseCase(healthConnectPreferences),
            observeCloudSyncStatus = ObserveCloudSyncStatusUseCase(cloudSync),
        )
        return DashboardViewModel(
            observeDashboardState = observeDashboardState,
            loadGraphDetail = LoadGraphDetailUseCase(
                liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
                muscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
                muscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
                liftRepository = FakeLiftRepository(),
                trainingLogRepository = FakeTrainingLogRepository(),
            ),
            setDashboardBoardTab = SetDashboardBoardTabUseCase(
                dashboardPreferences,
            ),
            requestCloudSync = RequestCloudSyncUseCase(cloudSync),
            diagnosticDrain = DiagnosticDrain(drainLogging),
            zone = testZone,
            clock = { now ?: Instant.now() },
        )
    }
}

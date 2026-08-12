package com.stratum

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectMetricsPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.dashboard.LoadDashboardUseCase
import com.stratum.domain.dashboard.LoadGraphDetailUseCase
import com.stratum.domain.dashboard.ObserveDashboardPreferencesUseCase
import com.stratum.domain.dashboard.SetDashboardBoardTabUseCase
import com.stratum.domain.healthconnect.DailyStepCount
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.healthconnect.HealthMetricType
import com.stratum.domain.healthconnect.HealthMetricsSnapshot
import com.stratum.domain.healthconnect.LoadHealthConnectMetricsUseCase
import com.stratum.domain.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.domain.healthconnect.WeightSample
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.sync.ObserveCloudSyncStatusUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(false, viewModel.healthUiState.value.visible)
        assertEquals(DashboardUiState.Empty, viewModel.uiState.value)
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
        assertEquals(false, viewModel.healthUiState.value.visible)
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

        assertEquals(DashboardUiState.Empty, viewModel.uiState.value)
        val health = viewModel.healthUiState.value
        assertEquals(true, health.visible)
        assertEquals("8,432", health.activity.todayStepsText)
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
        assertTrue(viewModel.healthUiState.value.weight.latestText?.contains("lb") == true)

        weightPrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))
        advanceUntilIdle()
        assertEquals("80.0 kg", viewModel.healthUiState.value.weight.latestText)
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
        assertEquals(false, viewModel.healthUiState.value.visible)

        healthPrefs.setPreferences(HealthConnectPreferences(enabled = false))
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(false, viewModel.healthUiState.value.visible)
    }

    private fun buildViewModel(
        healthConnectPreferences: FakeHealthConnectPreferencesRepository =
            FakeHealthConnectPreferencesRepository(),
        healthConnectMetrics: FakeHealthConnectMetricsPort = FakeHealthConnectMetricsPort(),
        weightDisplayPreferences: FakeWeightDisplayPreferencesRepository =
            FakeWeightDisplayPreferencesRepository(),
        now: Instant? = null,
    ): DashboardViewModel {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        return DashboardViewModel(
            loadDashboard = LoadDashboardUseCase(
                muscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
                liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
                muscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
                liftRepository = FakeLiftRepository(),
                trainingLogRepository = FakeTrainingLogRepository(),
                programRepository = FakeProgramRepository(program),
                loggingPort = FakeLoggingPort(),
            ),
            loadGraphDetail = LoadGraphDetailUseCase(
                liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
                muscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
                muscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
                liftRepository = FakeLiftRepository(),
                trainingLogRepository = FakeTrainingLogRepository(),
                loggingPort = FakeLoggingPort(),
            ),
            setDashboardBoardTab = SetDashboardBoardTabUseCase(
                FakeDashboardPreferencesRepository(),
                FakeLoggingPort(),
            ),
            observeDashboardPreferences =
                ObserveDashboardPreferencesUseCase(FakeDashboardPreferencesRepository()),
            observeCloudSyncStatus = ObserveCloudSyncStatusUseCase(FakeCloudSyncPort()),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(weightDisplayPreferences),
            observeHealthConnectPreferences =
                ObserveHealthConnectPreferencesUseCase(healthConnectPreferences),
            loadHealthConnectMetrics = LoadHealthConnectMetricsUseCase(
                healthConnectMetrics,
                healthConnectPreferences,
            ),
            zone = testZone,
            clock = { now ?: Instant.now() },
        )
    }
}

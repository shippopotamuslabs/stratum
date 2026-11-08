package com.stratum.application.dashboard

import app.cash.turbine.test
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import com.stratum.application.healthconnect.LoadHealthConnectMetricsUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.sync.ObserveCloudSyncStatusUseCase
import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectMetricsPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.health.HealthMetricType
import com.stratum.domain.health.HealthMetricsPages
import com.stratum.domain.healthconnect.HealthConnectPreferences
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.sync.SyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObserveDashboardStateUseCaseTest {

    private val utc: ZoneId = ZoneOffset.UTC

    @Test
    fun `initial load emits board data with hidden health`() = runTest {
        val harness = buildHarness()
        harness.stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek(), 10.0)))

        harness.useCase(emptyFlow(), utc, clock = { NOW }).test {
            val state = awaitItem()

            val loaded = state.board as LoadDashboardResult.Loaded
            assertEquals(listOf(MuscleGroup.Chest), loaded.summary.muscles.map { it.muscle })
            assertEquals(HealthMetricsPages.Hidden, state.healthPages)
            assertEquals(DashboardBoardTab.Muscles, state.selectedTab)
            assertEquals(Weight.WeightUnit.Lb, state.displayUnit)
            assertEquals(0, harness.metricsPort.readSnapshotCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync busy to idle triggers a board reload`() = runTest {
        val harness = buildHarness()
        harness.stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek(), 10.0)))

        turbineScope {
            val turbine = harness.useCase(emptyFlow(), utc, clock = { NOW }).testIn(backgroundScope)
            val first = turbine.awaitItem()
            assertEquals(
                listOf(MuscleGroup.Chest),
                (first.board as LoadDashboardResult.Loaded).summary.muscles.map { it.muscle },
            )

            harness.stimulusRepo.seed(
                listOf(
                    stimulus(MuscleGroup.Chest, recentWeek(), 10.0),
                    stimulus(MuscleGroup.Quad, recentWeek(), 14.0),
                ),
            )
            harness.syncPort.setStatus(SyncStatus.Syncing)
            // Let the collector observe Syncing before Idle; StateFlow conflates rapid sets.
            testScheduler.advanceUntilIdle()
            harness.syncPort.setStatus(SyncStatus.Idle)

            val reloaded = turbine.awaitItem()
            assertEquals(
                setOf(MuscleGroup.Chest, MuscleGroup.Quad),
                (reloaded.board as LoadDashboardResult.Loaded).summary.muscles
                    .map { it.muscle }.toSet(),
            )
            // A sync-settled reload builds a new summary instance.
            assertNotSame(
                (first.board as LoadDashboardResult.Loaded).summary,
                (reloaded.board as LoadDashboardResult.Loaded).summary,
            )
            turbine.cancel()
        }
    }

    @Test
    fun `health disabled emits hidden and enabling loads pages`() = runTest {
        val harness = buildHarness()
        harness.stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek(), 10.0)))
        harness.metricsPort.available = true
        harness.metricsPort.granted = setOf(HealthMetricType.Steps)

        harness.useCase(emptyFlow(), utc, clock = { NOW }).test {
            val first = awaitItem()
            assertEquals(HealthMetricsPages.Hidden, first.healthPages)
            assertEquals(0, harness.metricsPort.readSnapshotCount)

            harness.healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))

            val loaded = awaitItem()
            assertTrue(loaded.healthPages.visible)
            assertEquals(1, harness.metricsPort.readSnapshotCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `display unit and tab changes re-emit without a board reload`() = runTest {
        val harness = buildHarness()
        harness.stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek(), 10.0)))

        harness.useCase(emptyFlow(), utc, clock = { NOW }).test {
            val first = awaitItem()

            harness.weightPrefs.seed(WeightDisplayPreferences(Weight.WeightUnit.Kg))

            val unitChanged = awaitItem()
            assertEquals(Weight.WeightUnit.Kg, unitChanged.displayUnit)
            assertSame(
                (first.board as LoadDashboardResult.Loaded).summary,
                (unitChanged.board as LoadDashboardResult.Loaded).summary,
            )
            assertSame(first.healthPages, unitChanged.healthPages)
            assertEquals(0, harness.metricsPort.readSnapshotCount)

            harness.dashboardPrefs.seed(DashboardBoardTab.Lifts)

            val tabChanged = awaitItem()
            assertEquals(DashboardBoardTab.Lifts, tabChanged.selectedTab)
            assertSame(
                (first.board as LoadDashboardResult.Loaded).summary,
                (tabChanged.board as LoadDashboardResult.Loaded).summary,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `health load racing a disable is dropped`() = runTest {
        val harness = buildHarness()
        harness.stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek(), 10.0)))
        harness.metricsPort.available = true
        harness.metricsPort.granted = setOf(HealthMetricType.Steps)
        val gate = CompletableDeferred<Unit>()
        harness.metricsPort.snapshotGate = gate
        harness.healthPrefs.setPreferences(HealthConnectPreferences(enabled = true))

        turbineScope {
            val turbine = harness.useCase(emptyFlow(), utc, clock = { NOW }).testIn(backgroundScope)
            // Settle the initial load so it is parked on the snapshot gate.
            testScheduler.advanceUntilIdle()
            turbine.expectNoEvents()

            harness.healthPrefs.setPreferences(HealthConnectPreferences(enabled = false))
            testScheduler.advanceUntilIdle()
            gate.complete(Unit)

            val state = turbine.awaitItem()
            assertEquals(HealthMetricsPages.Hidden, state.healthPages)
            assertEquals(1, harness.metricsPort.readSnapshotCount)
            turbine.cancel()
        }
    }

    private fun buildHarness(): Harness {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val healthPrefs = FakeHealthConnectPreferencesRepository()
        val metricsPort = FakeHealthConnectMetricsPort()
        val syncPort = FakeCloudSyncPort()
        val weightPrefs = FakeWeightDisplayPreferencesRepository()
        val dashboardPrefs = FakeDashboardPreferencesRepository()
        val loadDashboard = LoadDashboardUseCase(
            muscleWeeklyStimulusRepository = stimulusRepo,
            liftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
            muscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
            liftRepository = FakeLiftRepository(),
            trainingLogRepository = FakeTrainingLogRepository(),
            programRepository = FakeProgramRepository(null),
        )
        val useCase = ObserveDashboardStateUseCase(
            loadDashboard = loadDashboard,
            loadHealthConnectMetrics = LoadHealthConnectMetricsUseCase(
                metricsPort = metricsPort,
                preferencesPort = healthPrefs,
            ),
            observeDashboardPreferences = ObserveDashboardPreferencesUseCase(dashboardPrefs),
            observeWeightDisplayPreferences = ObserveWeightDisplayPreferencesUseCase(weightPrefs),
            observeHealthConnectPreferences = ObserveHealthConnectPreferencesUseCase(healthPrefs),
            observeCloudSyncStatus = ObserveCloudSyncStatusUseCase(syncPort),
        )
        return Harness(
            useCase = useCase,
            stimulusRepo = stimulusRepo,
            metricsPort = metricsPort,
            healthPrefs = healthPrefs,
            weightPrefs = weightPrefs,
            dashboardPrefs = dashboardPrefs,
            syncPort = syncPort,
        )
    }

    private class Harness(
        val useCase: ObserveDashboardStateUseCase,
        val stimulusRepo: FakeMuscleWeeklyStimulusRepository,
        val metricsPort: FakeHealthConnectMetricsPort,
        val healthPrefs: FakeHealthConnectPreferencesRepository,
        val weightPrefs: FakeWeightDisplayPreferencesRepository,
        val dashboardPrefs: FakeDashboardPreferencesRepository,
        val syncPort: FakeCloudSyncPort,
    )

    private fun recentWeek(): Instant = NOW.minus(7, ChronoUnit.DAYS)

    private fun stimulus(
        muscle: MuscleGroup,
        weekStart: Instant,
        hardSets: Double,
    ): MuscleWeeklyStimulus =
        MuscleWeeklyStimulus(
            muscle = muscle,
            weekStart = weekStart,
            hardSets = hardSets,
            sessions = 1,
            meanRir = 2.0,
            intensityProxy = null,
            compositionHash = "",
            deloadFlag = false,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private companion object {
        val NOW: Instant = Instant.parse("2025-06-01T00:00:00Z")
    }
}

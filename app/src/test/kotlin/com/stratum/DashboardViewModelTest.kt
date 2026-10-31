package com.stratum

import com.stratum.core.testing.dashboard.FakeDashboardPreferencesRepository
import com.stratum.core.testing.healthconnect.FakeHealthConnectMetricsPort
import com.stratum.core.testing.healthconnect.FakeHealthConnectPreferencesRepository
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyOutcomeIndexRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.sync.FakeCloudSyncPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.withStart
import com.stratum.domain.dashboard.DashboardBoardTab
import com.stratum.domain.dashboard.DashboardPreferences
import com.stratum.application.dashboard.LoadDashboardUseCase
import com.stratum.application.dashboard.LoadGraphDetailUseCase
import com.stratum.application.dashboard.ObserveDashboardPreferencesUseCase
import com.stratum.application.healthconnect.LoadHealthConnectMetricsUseCase
import com.stratum.application.healthconnect.ObserveHealthConnectPreferencesUseCase
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.application.dashboard.SetDashboardBoardTabUseCase
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.application.sync.ObserveCloudSyncStatusUseCase
import com.stratum.application.sync.RequestCloudSyncUseCase
import com.stratum.domain.sync.SyncStatus
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
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
class DashboardViewModelTest {

    private val testZone: ZoneId = ZoneOffset.UTC
    private val newYork: ZoneId = ZoneId.of("America/New_York")
    @Test
    fun `emits Empty when no weekly data exists`() = runTest {
        val viewModel = buildViewModel()

        assertEquals(DashboardUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `load maps use-case failure to Error not Empty`() = runTest {
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        stimulusRepo.getSinceError = IllegalStateException("dashboard boom")
        val viewModel = buildViewModel(stimulusRepo = stimulusRepo)
        advanceUntilIdle()

        assertEquals(DashboardUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `openLiftGraph maps use-case failure to Error and retry recovers`() = runTest {
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.getAllError = IllegalStateException("graph boom")
        val viewModel = buildViewModel(performanceRepo = performanceRepo)

        viewModel.openLiftGraph("bench")
        advanceUntilIdle()

        assertEquals(GraphDetailUiState.Error, viewModel.graphDetailState.value)

        performanceRepo.getAllError = null
        viewModel.retryGraphDetail()
        advanceUntilIdle()

        assertEquals(GraphDetailUiState.Empty, viewModel.graphDetailState.value)
    }

    @Test
    fun `maps muscle series to UI with log-scale progress badge`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val now = Instant.now()
        val w0 = now.minus(28, ChronoUnit.DAYS)
        val w1 = now.minus(21, ChronoUnit.DAYS)
        val w2 = now.minus(14, ChronoUnit.DAYS)
        val w3 = now.minus(7, ChronoUnit.DAYS)
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 12.0),
                stimulus(MuscleGroup.Chest, w1, 13.0),
                stimulus(MuscleGroup.Chest, w2, 14.0),
                stimulus(MuscleGroup.Chest, w3, 15.0),
            ),
        )
        // Log-scale deltas: ~0.005/wk — would be Flat under the old 0.5 kg threshold.
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        outcomeRepo.seed(
            listOf(
                outcome(MuscleGroup.Chest, w0, 0.0, contributing = 2),
                outcome(MuscleGroup.Chest, w1, 0.005, contributing = 2),
                outcome(MuscleGroup.Chest, w2, 0.010, contributing = 2),
                outcome(MuscleGroup.Chest, w3, 0.015, contributing = 2),
            ),
        )
        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        val muscle = loaded.muscles.single { it.name == "Chest" }
        assertEquals("13 sets/wk", muscle.headlineVolume)
        assertEquals(TrendBadge.Up, muscle.badge, "log-scale rise above 0.002/wk → Up")
        assertEquals(2, muscle.contributingLifts)
        assertTrue(muscle.progressPoints.isNotEmpty(), "progress series mapped to UI points")
        assertEquals(4, muscle.volumePoints.size)
    }

    @Test
    fun `muscle badge is Flat for sub-threshold log deltas`() = runTest {
        val liftRepo = FakeLiftRepository()
        val now = Instant.now()
        val w0 = now.minus(21, ChronoUnit.DAYS)
        val w1 = now.minus(14, ChronoUnit.DAYS)
        val w2 = now.minus(7, ChronoUnit.DAYS)
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 10.0),
                stimulus(MuscleGroup.Chest, w1, 10.0),
                stimulus(MuscleGroup.Chest, w2, 10.0),
            ),
        )
        // Slope ≈ 0.0005/wk — below MUSCLE_PROGRESS_SLOPE_THRESHOLD (0.002).
        val outcomeRepo = FakeMuscleWeeklyOutcomeIndexRepository()
        outcomeRepo.seed(
            listOf(
                outcome(MuscleGroup.Chest, w0, 0.0),
                outcome(MuscleGroup.Chest, w1, 0.0005),
                outcome(MuscleGroup.Chest, w2, 0.0010),
            ),
        )
        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            outcomeIndexRepo = outcomeRepo,
            liftRepo = liftRepo,
        )

        val muscle = (viewModel.uiState.value as DashboardUiState.Loaded).muscles.single()
        assertEquals(TrendBadge.Flat, muscle.badge, "tiny log-index noise stays Flat")
    }

    @Test
    fun `maps lift series to Upper section with e1RM and sessions`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val now = Instant.now()
        val w0 = now.minus(21, ChronoUnit.DAYS)
        val w1 = now.minus(14, ChronoUnit.DAYS)
        val w2 = now.minus(7, ChronoUnit.DAYS)
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(
            listOf(
                perf(benchId, w0, 100.0), perf(benchId, w1, 102.0), perf(benchId, w2, 104.0),
            ),
        )
        val firstSetAt = now.minus(70, ChronoUnit.DAYS)
        val trainingLog = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(firstSetAt)
        val set = WorkoutLoggingFixtures.loggedSet(id = "set-1", sessionId = session.id).copy(
            liftId = benchId,
            completedAt = firstSetAt,
        )
        trainingLog.replaceAll(listOf(session), listOf(set))
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = trainingLog,
            programLiftIds = setOf(benchId),
        )

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        assertEquals(listOf(SECTION_UPPER), loaded.liftSections.map { it.title })
        val liftUi = loaded.liftSections.single().lifts.single { it.name == "Bench Press" }
        assertTrue(liftUi.e1rmText?.contains("lb") == true, "e1RM text includes unit (default lbs)")
        assertEquals(TrendBadge.Up, liftUi.badge, "improving e1RM → Up badge (kg threshold)")
        assertEquals(3, liftUi.performancePoints.size)
        assertEquals(3, liftUi.sessions)
        assertEquals(13, loaded.windowWeeks)
    }

    @Test
    fun `maps upper and lower lifts into separate sections`() = runTest {
        val benchId = LiftId("bench")
        val squatId = LiftId("squat")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(squatId, "Back Squat", MuscleGroup.Quad),
            ),
        )
        val now = Instant.now()
        val week = now.minus(7, ChronoUnit.DAYS)
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(
            listOf(
                perf(benchId, week, 100.0),
                perf(squatId, week, 140.0),
            ),
        )
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId, squatId),
        )

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        assertEquals(listOf(SECTION_UPPER, SECTION_LOWER), loaded.liftSections.map { it.title })
        assertEquals(listOf("Bench Press"), loaded.liftSections[0].lifts.map { it.name })
        assertEquals(listOf("Back Squat"), loaded.liftSections[1].lifts.map { it.name })
    }

    @Test
    fun `alignMuscleSeries shares week indices across progress and volume`() {
        val w0 = Instant.parse("2025-01-01T00:00:00Z")
        val w1 = Instant.parse("2025-01-08T00:00:00Z")
        val w2 = Instant.parse("2025-01-15T00:00:00Z")
        // Progress missing middle week; volume has all three.
        val progress = listOf(
            TrendPoint(w0, 0.01),
            TrendPoint(w2, 0.02),
        )
        val volume = listOf(
            TrendPoint(w0, 8.0),
            TrendPoint(w1, 10.0),
            TrendPoint(w2, 12.0),
        )

        val (progressUi, volumeUi) = alignMuscleSeries(progress, volume)

        assertEquals(listOf(0, 2), progressUi.map { it.weekIndex })
        assertEquals(listOf(0.01f, 0.02f), progressUi.map { it.value })
        assertEquals(listOf(0, 1, 2), volumeUi.map { it.weekIndex })
        assertEquals(listOf(8f, 10f, 12f), volumeUi.map { it.value })
    }

    @Test
    fun `reload keeps Loaded visible instead of flashing Empty`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val now = Instant.now()
        val w0 = now.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, w0, 10.0)))
        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            liftRepo = liftRepo,
        )

        assertTrue(viewModel.uiState.value is DashboardUiState.Loaded)
        viewModel.load()
        assertTrue(viewModel.uiState.value is DashboardUiState.Loaded, "keep Loaded on reload")
    }

    @Test
    @DisplayName("reloads when cloud sync returns to Idle after busy (post-hydrate weekly rebuild)")
    fun reloadsAfterSyncIdle() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val cloudSync = FakeCloudSyncPort()
        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            liftRepo = liftRepo,
            cloudSync = cloudSync,
        )
        advanceUntilIdle()
        assertEquals(DashboardUiState.Empty, viewModel.uiState.value)

        // Simulate post-pull rebuild filling weekly tables, then Idle.
        val now = Instant.now()
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, now.minus(7, ChronoUnit.DAYS), 12.0)))
        cloudSync.setStatus(SyncStatus.Hydrating)
        cloudSync.setStatus(SyncStatus.Idle)
        advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value is DashboardUiState.Loaded,
            "dashboard should reload after sync Idle once weekly series exist",
        )
    }

    @Test
    @DisplayName("openLiftGraph loads full history into Ready detail state")
    fun openLiftGraphReady() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val now = Instant.now()
        val old = now.minus(140, ChronoUnit.DAYS)
        val recent = now.minus(7, ChronoUnit.DAYS)
        performanceRepo.seed(
            listOf(
                perf(benchId, old, 90.0),
                perf(benchId, recent, 110.0),
            ),
        )
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        viewModel.openLiftGraph(benchId.value)
        advanceUntilIdle()

        val detail = viewModel.graphDetailState.value as GraphDetailUiState.Ready
        assertEquals("Bench Press", detail.title)
        assertEquals(GraphDetailKindUi.Lift, detail.kind)
        assertEquals(GraphViewMode.Primary, detail.selectedMode)
        assertEquals(GraphTimeRangeUi.ThreeMonths, detail.selectedRange, "default matches board window")
        assertEquals(1, detail.primaryPoints.size, "3M filters out the 140-day-old week")
        assertTrue(detail.secondaryPoints == null, "single-series tabs hide overlay")
        assertEquals(
            listOf(GraphViewMode.Primary, GraphViewMode.Volume, GraphViewMode.Dual),
            detail.modes,
        )

        viewModel.selectGraphTimeRange(GraphTimeRangeUi.All)
        val allRange = viewModel.graphDetailState.value as GraphDetailUiState.Ready
        assertEquals(2, allRange.primaryPoints.size, "All shows full history")
    }

    @Test
    @DisplayName("selectGraphMode Volume switches primary series to hard sets")
    fun switchModeToVolume() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val now = Instant.now()
        performanceRepo.seed(listOf(perf(benchId, now.minus(7, ChronoUnit.DAYS), 100.0)))
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        viewModel.openLiftGraph(benchId.value)
        advanceUntilIdle()
        viewModel.selectGraphMode(GraphViewMode.Volume)

        val detail = viewModel.graphDetailState.value as GraphDetailUiState.Ready
        assertEquals(GraphViewMode.Volume, detail.selectedMode)
        assertEquals(4f, detail.primaryPoints.single().value)
        assertTrue(detail.secondaryPoints == null)
    }

    @Test
    @DisplayName("selectGraphMode Dual exposes e1RM primary and volume secondary")
    fun dualMode() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val now = Instant.now()
        performanceRepo.seed(
            listOf(
                perf(benchId, now.minus(14, ChronoUnit.DAYS), 100.0),
                perf(benchId, now.minus(7, ChronoUnit.DAYS), 105.0),
            ),
        )
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        viewModel.openLiftGraph(benchId.value)
        advanceUntilIdle()
        viewModel.selectGraphMode(GraphViewMode.Dual)

        val detail = viewModel.graphDetailState.value as GraphDetailUiState.Ready
        assertEquals(GraphViewMode.Dual, detail.selectedMode)
        assertEquals(2, detail.primaryPoints.size)
        assertEquals(2, detail.secondaryPoints?.size)
        assertEquals(com.stratum.core.ui.component.DetailYScale.Weight, detail.primaryYScale)
        assertEquals(com.stratum.core.ui.component.DetailYScale.Volume, detail.secondaryYScale)
    }

    @Test
    @DisplayName("dismissGraphDetail returns to Hidden")
    fun dismissDetail() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(listOf(perf(benchId, Instant.now().minus(7, ChronoUnit.DAYS), 100.0)))
        val viewModel = buildViewModel(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        viewModel.openLiftGraph(benchId.value)
        advanceUntilIdle()
        viewModel.dismissGraphDetail()
        assertEquals(GraphDetailUiState.Hidden, viewModel.graphDetailState.value)
    }

    @Test
    @DisplayName("formatHardSetHeadline keeps half-sets and pluralizes")
    fun hardSetHeadlineFormat() {
        assertEquals("1 set", formatHardSetHeadline(1.0))
        assertEquals("12 sets", formatHardSetHeadline(12.0))
        assertEquals("0.5 sets", formatHardSetHeadline(0.5))
        assertEquals("12.5 sets", formatHardSetHeadline(12.5))
    }

    @Test
    @DisplayName("shortWeekdayLabel uses R for Thursday and Sa/Su for weekend")
    fun shortWeekdayLabels() {
        // Local midnights for Mon–Su in America/New_York (EDT, UTC-4).
        val monday = LocalDate.of(2025, 6, 9).atStartOfDay(newYork).toInstant()
        assertEquals("M", shortWeekdayLabel(monday, newYork))
        assertEquals("T", shortWeekdayLabel(monday.plus(1, ChronoUnit.DAYS), newYork))
        assertEquals("W", shortWeekdayLabel(monday.plus(2, ChronoUnit.DAYS), newYork))
        assertEquals("R", shortWeekdayLabel(monday.plus(3, ChronoUnit.DAYS), newYork))
        assertEquals("F", shortWeekdayLabel(monday.plus(4, ChronoUnit.DAYS), newYork))
        assertEquals("Sa", shortWeekdayLabel(monday.plus(5, ChronoUnit.DAYS), newYork))
        assertEquals("Su", shortWeekdayLabel(monday.plus(6, ChronoUnit.DAYS), newYork))
    }

    @Test
    @DisplayName("shortWeekdayLabel keeps Wednesday for evening Instant after UTC midnight")
    fun shortWeekdayLabelLocalAfterUtcFlip() {
        // Wed 2026-07-22 20:30 EDT = Thu 00:30 UTC — day-start key is still Wednesday local.
        val wedEvening = Instant.parse("2026-07-23T00:30:00Z")
        val wedLocalMidnight = LocalDate.of(2026, 7, 22).atStartOfDay(newYork).toInstant()
        assertEquals(LocalDate.of(2026, 7, 22), wedEvening.atZone(newYork).toLocalDate())
        assertEquals("W", shortWeekdayLabel(wedLocalMidnight, newYork))
        // Bucket key for that evening is local midnight of Wed, not Thu UTC.
        assertEquals("W", shortWeekdayLabel(wedLocalMidnight, newYork))
        assertEquals("R", shortWeekdayLabel(wedLocalMidnight.plus(1, ChronoUnit.DAYS), newYork))
    }

    @Test
    @DisplayName("7-Day board labels end on local Wednesday when zone is America/New_York")
    fun sevenDayLabelsUsePinnedZone() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        // Wed 2026-07-22 20:30 EDT — already Thursday UTC.
        val asOf = Instant.parse("2026-07-23T00:30:00Z")
        val trainingLog = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        trainingLog.seedSession(session)
        trainingLog.seedSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = session.id,
                exerciseNameRaw = "Bench",
                liftId = benchId,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Kg),
                reps = 8,
                rir = 2,
                completedAt = asOf,
                liftContext = LoggedLiftContext(benchId.value),
            ),
        )
        // Seed weekly stimulus so Loaded path is hit even if daily is empty (shouldn't be).
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, asOf.minus(7, ChronoUnit.DAYS), 8.0)))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(listOf(perf(benchId, asOf.minus(7, ChronoUnit.DAYS), 100.0)))

        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = trainingLog,
            programLiftIds = setOf(benchId),
            zone = newYork,
            now = asOf,
        )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        val daily = loaded.runningDailyVolume.single { it.name == "Chest" }
        assertEquals(7, daily.dayLabels.size)
        assertEquals("W", daily.dayLabels.last(), "rightmost label must be local Wednesday")
    }

    @Test
    @DisplayName("openMuscleDailyVolumeGraph defaults to Volume mode and 1W range")
    fun openDailyVolumeGraph() = runTest {
        val benchId = LiftId("bench")
        val bench = lift(benchId, "Bench Press", MuscleGroup.Chest)
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(bench))
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val now = Instant.now()
        val recent = now.minus(3, ChronoUnit.DAYS)
        performanceRepo.seed(listOf(perf(benchId, recent, 100.0)))
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recent, 8.0)))

        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedLiftForMuscleFilter(bench)
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1")
        trainingLog.seedSession(session)
        trainingLog.seedSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = session.id,
                exerciseNameRaw = "Bench",
                liftId = benchId,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Kg),
                reps = 8,
                rir = 2,
                completedAt = recent,
                liftContext = LoggedLiftContext(benchId.value),
            ),
        )

        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = trainingLog,
            programLiftIds = setOf(benchId),
        )
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        val daily = loaded.runningDailyVolume.single { it.name == "Chest" }
        assertEquals("1 set", daily.headlineSets)
        assertEquals(7, daily.volumePoints.size)
        assertEquals(7, daily.dayLabels.size, "rolling weekday labels for each bar")
        assertTrue(daily.dayLabels.all { it in setOf("M", "T", "W", "R", "F", "Sa", "Su") })

        viewModel.openMuscleDailyVolumeGraph("Chest")
        advanceUntilIdle()

        val detail = viewModel.graphDetailState.value as GraphDetailUiState.Ready
        assertEquals(GraphDetailKindUi.MuscleDailyVolume, detail.kind)
        assertEquals(GraphViewMode.Volume, detail.selectedMode)
        assertEquals(GraphTimeRangeUi.OneWeek, detail.selectedRange)
        assertEquals(listOf(GraphViewMode.Volume), detail.modes)
        assertTrue(GraphTimeRangeUi.OneWeek in detail.ranges)
        assertEquals(7, detail.primaryPoints.size, "1W zero-fills rest days")
    }

    @Test
    fun `selectedTab defaults to Muscles`() = runTest {
        val viewModel = buildViewModel()
        advanceUntilIdle()

        assertEquals(DashboardBoardTabUi.Muscles, viewModel.selectedTab.value)
    }

    @Test
    fun `selectedTab restores persisted tab from preferences`() = runTest {
        val prefs = FakeDashboardPreferencesRepository(
            DashboardPreferences(selectedTab = DashboardBoardTab.SevenDay),
        )
        val viewModel = buildViewModel(dashboardPreferences = prefs)
        advanceUntilIdle()

        assertEquals(DashboardBoardTabUi.SevenDay, viewModel.selectedTab.value)
    }

    @Test
    fun `selectTab updates state only after prefs emit and persists`() = runTest {
        val prefs = FakeDashboardPreferencesRepository()
        val viewModel = buildViewModel(dashboardPreferences = prefs)
        advanceUntilIdle()

        viewModel.selectTab(DashboardBoardTabUi.Lifts)
        advanceUntilIdle()

        assertEquals(DashboardBoardTabUi.Lifts, viewModel.selectedTab.value)
        assertEquals(DashboardBoardTab.Lifts, prefs.current.selectedTab)
    }

    @Test
    fun `selectTab is no-op when tab already selected`() = runTest {
        val prefs = FakeDashboardPreferencesRepository()
        val viewModel = buildViewModel(dashboardPreferences = prefs)
        advanceUntilIdle()

        viewModel.selectTab(DashboardBoardTabUi.Muscles)
        advanceUntilIdle()

        assertEquals(DashboardBoardTabUi.Muscles, viewModel.selectedTab.value)
        assertEquals(DashboardBoardTab.Muscles, prefs.current.selectedTab)
    }

    @Test
    fun `board tab ui index round-trips and unknown falls back to Muscles`() {
        for (tab in DashboardBoardTabUi.entries) {
            assertEquals(tab, DashboardBoardTabUi.fromIndex(tab.index))
            assertEquals(tab, tab.toDomain().toUi())
        }
        assertEquals(DashboardBoardTabUi.Muscles, DashboardBoardTabUi.fromIndex(99))
    }

    private fun buildViewModel(
        stimulusRepo: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
        performanceRepo: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        outcomeIndexRepo: FakeMuscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
        programLiftIds: Set<LiftId> = emptySet(),
        cloudSync: FakeCloudSyncPort = FakeCloudSyncPort(),
        dashboardPreferences: FakeDashboardPreferencesRepository = FakeDashboardPreferencesRepository(),
        weightDisplayPreferences: FakeWeightDisplayPreferencesRepository =
            FakeWeightDisplayPreferencesRepository(),
        healthConnectPreferences: FakeHealthConnectPreferencesRepository =
            FakeHealthConnectPreferencesRepository(),
        healthConnectMetrics: FakeHealthConnectMetricsPort = FakeHealthConnectMetricsPort(),
        zone: ZoneId = testZone,
        now: Instant? = null,
    ): DashboardViewModel {
        val baseProgram = WorkoutLoggingFixtures.twoWorkoutProgram()
        val program = if (programLiftIds.isEmpty()) {
            baseProgram
        } else {
            val templateLift = WorkoutLoggingFixtures.workoutLiftWithDoubleProgression()
            val firstWorkout = baseProgram.workouts.first()
            baseProgram.copy(
                workouts = baseProgram.workouts.mapIndexed { index, w ->
                    if (index == 0) {
                        w.copy(
                            workoutLifts = programLiftIds.mapIndexed { i, lid ->
                                templateLift.copy(liftId = lid, workoutId = firstWorkout.id, position = i)
                            },
                        )
                    } else {
                        w
                    }
                },
            )
        }
        val useCase = LoadDashboardUseCase(
            muscleWeeklyStimulusRepository = stimulusRepo,
            liftWeeklyPerformanceRepository = performanceRepo,
            muscleWeeklyOutcomeIndexRepository = outcomeIndexRepo,
            liftRepository = liftRepo,
            trainingLogRepository = trainingLog,
            programRepository = FakeProgramRepository(program),
            loggingPort = FakeLoggingPort(),
        )
        val detailUseCase = LoadGraphDetailUseCase(
            liftWeeklyPerformanceRepository = performanceRepo,
            muscleWeeklyStimulusRepository = stimulusRepo,
            muscleWeeklyOutcomeIndexRepository = outcomeIndexRepo,
            liftRepository = liftRepo,
            trainingLogRepository = trainingLog,
            loggingPort = FakeLoggingPort(),
        )
        return DashboardViewModel(
            loadDashboard = useCase,
            loadGraphDetail = detailUseCase,
            setDashboardBoardTab = SetDashboardBoardTabUseCase(
                dashboardPreferences,
                FakeLoggingPort(),
            ),
            observeDashboardPreferences = ObserveDashboardPreferencesUseCase(dashboardPreferences),
            observeCloudSyncStatus = ObserveCloudSyncStatusUseCase(cloudSync),
            observeWeightDisplayPreferences =
                ObserveWeightDisplayPreferencesUseCase(weightDisplayPreferences),
            observeHealthConnectPreferences =
                ObserveHealthConnectPreferencesUseCase(healthConnectPreferences),
            loadHealthConnectMetrics = LoadHealthConnectMetricsUseCase(
                healthConnectMetrics,
                healthConnectPreferences,
                FakeLoggingPort(),
            ),
            requestCloudSync = RequestCloudSyncUseCase(cloudSync),
            zone = zone,
            clock = { now ?: Instant.now() },
        )
    }

    private fun lift(id: LiftId, name: String, muscle: MuscleGroup): Lift = Lift(
        id = id,
        name = name,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun stimulus(muscle: MuscleGroup, weekStart: Instant, hardSets: Double): MuscleWeeklyStimulus =
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

    private fun perf(liftId: LiftId, weekStart: Instant, peakE1rm: Double): LiftWeeklyPerformance =
        LiftWeeklyPerformance(
            liftId = liftId,
            weekStart = weekStart,
            peakE1rm = peakE1rm,
            smoothedE1rm = peakE1rm,
            logE1rm = kotlin.math.ln(peakE1rm.coerceAtLeast(1.0)),
            hardSets = 4.0,
            sessions = 1,
            meanRir = 2.0,
            reliability = 0.9,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private fun outcome(
        muscle: MuscleGroup,
        weekStart: Instant,
        indexValue: Double,
        contributing: Int = 2,
    ): MuscleWeeklyOutcomeIndex =
        MuscleWeeklyOutcomeIndex(
            muscle = muscle,
            weekStart = weekStart,
            indexValue = indexValue,
            indexStdError = 0.01,
            contributingLiftCount = contributing,
            effectiveLiftCount = contributing,
            primaryCoverage = 1.0,
            compositionHash = "h",
            compositionDrift = 0.0,
            qualityFlags = emptySet(),
        )
}

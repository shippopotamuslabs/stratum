package com.stratum

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.dashboard.LoadDashboardUseCase
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class DashboardViewModelTest {
    @Test
    fun `emits Empty when no weekly data exists`() = runTest {
        val viewModel = buildViewModel()

        assertEquals(DashboardUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `maps muscle series to UI with badges and points`() = runTest {
        val benchId = LiftId("bench")
        val inclineId = LiftId("incline")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(inclineId, "Incline Bench", MuscleGroup.Chest),
            ),
        )
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
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        // Both lifts improving → composite progress should be Up.
        performanceRepo.seed(
            listOf(
                perf(benchId, w0, 100.0), perf(benchId, w1, 103.0), perf(benchId, w2, 106.0), perf(benchId, w3, 109.0),
                perf(inclineId, w0, 80.0), perf(inclineId, w1, 82.0), perf(inclineId, w2, 84.0), perf(inclineId, w3, 86.0),
            ),
        )
        val viewModel = buildViewModel(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val loaded = viewModel.uiState.value as DashboardUiState.Loaded
        val muscle = loaded.muscles.single { it.name == "Chest" }
        assertEquals("13 sets/wk", muscle.headlineVolume)
        assertEquals(TrendBadge.Up, muscle.badge, "improving lifts → Up badge")
        assertEquals(2, muscle.contributingLifts)
        assertTrue(muscle.progressPoints.isNotEmpty(), "progress series mapped to UI points")
        assertEquals(4, muscle.volumePoints.size)
    }

    @Test
    fun `maps lift series to UI with e1RM and tenure`() = runTest {
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
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").copy(startedAt = firstSetAt)
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
        val liftUi = loaded.lifts.single { it.name == "Bench Press" }
        assertTrue(liftUi.e1rmText?.contains("kg") == true, "e1RM text includes unit")
        assertEquals(TrendBadge.Up, liftUi.badge, "improving e1RM → Up badge")
        assertEquals(3, liftUi.performancePoints.size)
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

    private fun buildViewModel(
        stimulusRepo: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
        performanceRepo: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
        programLiftIds: Set<LiftId> = emptySet(),
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
            liftRepository = liftRepo,
            trainingLogRepository = trainingLog,
            programRepository = FakeProgramRepository(program),
        )
        return DashboardViewModel(loadDashboard = useCase)
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
}

package com.stratum.domain.dashboard

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleWeeklyStimulusRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.response.TrainingSignalQualityFlag
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadDashboardUseCaseTest {
    @Test
    fun `returns null when no weekly data exists in window`() = runTest {
        val useCase = buildUseCase()

        assertNull(useCase(now = NOW))
    }

    @Test
    fun `window cutoff excludes data older than 13 weeks`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        // Old data (20 weeks ago) — outside the window.
        val oldWeek = NOW.minus(140, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, oldWeek, 10.0)))
        performanceRepo.seed(listOf(performance(benchId, oldWeek, 100.0)))
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
        )

        assertNull(useCase(now = NOW), "data older than the window should yield null")
    }

    @Test
    fun `window includes data within 13 weeks`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(28, ChronoUnit.DAYS)
        stimulusRepo.seed(listOf(stimulus(MuscleGroup.Chest, recentWeek, 10.0)))
        performanceRepo.seed(listOf(performance(benchId, recentWeek, 100.0)))
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW)
        assertNotNull(summary)
        assertEquals(1, summary!!.muscles.size)
        assertEquals(MuscleGroup.Chest, summary.muscles.first().muscle)
    }

    @Test
    fun `lifts tab filters to active program lifts`() = runTest {
        val benchId = LiftId("bench")
        val curlId = LiftId("curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(curlId, "DB Curl", MuscleGroup.Bicep),
            ),
        )
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        performanceRepo.seed(
            listOf(
                performance(benchId, recentWeek, 100.0),
                performance(curlId, recentWeek, 50.0),
            ),
        )
        // Active program has bench but not curl.
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW)!!
        val liftIds = summary.lifts.map { it.liftId }
        assertTrue(benchId in liftIds, "program lift should appear")
        assertTrue(curlId !in liftIds, "non-program lift should be excluded from lifts tab")
    }

    @Test
    fun `muscles tab rolls up across all lifts, not just program lifts`() = runTest {
        val benchId = LiftId("bench")
        val curlId = LiftId("curl")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(benchId, "Bench Press", MuscleGroup.Chest),
                lift(curlId, "DB Curl", MuscleGroup.Bicep),
            ),
        )
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, recentWeek, 10.0),
                stimulus(MuscleGroup.Bicep, recentWeek, 6.0),
            ),
        )
        performanceRepo.seed(
            listOf(
                performance(benchId, recentWeek, 100.0),
                performance(curlId, recentWeek, 50.0),
            ),
        )
        // Active program has only bench — but Bicep still appears in the muscles tab.
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(benchId),
        )

        val summary = useCase(now = NOW)!!
        val muscles = summary.muscles.map { it.muscle }.toSet()
        assertTrue(MuscleGroup.Chest in muscles, "program muscle should appear")
        assertTrue(MuscleGroup.Bicep in muscles, "non-program muscle with stimulus should still appear")
    }

    @Test
    fun `composite progress index requires at least 2 contributing lifts`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val stimulusRepo = FakeMuscleWeeklyStimulusRepository()
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        stimulusRepo.seed(
            listOf(
                stimulus(MuscleGroup.Chest, w0, 10.0),
                stimulus(MuscleGroup.Chest, w1, 10.0),
                stimulus(MuscleGroup.Chest, w2, 10.0),
            ),
        )
        performanceRepo.seed(
            listOf(
                performance(benchId, w0, 100.0),
                performance(benchId, w1, 105.0),
                performance(benchId, w2, 110.0),
            ),
        )
        val useCase = buildUseCase(
            stimulusRepo = stimulusRepo,
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
        )

        val chest = useCase(now = NOW)!!.muscles.first()
        assertNull(chest.progressSeries, "single contributing lift → insufficient for composite index")
    }

    @Test
    fun `lift tenure uses first-set date from training log`() = runTest {
        val benchId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(benchId, "Bench Press", MuscleGroup.Chest)))
        val firstSetAt = NOW.minus(70, ChronoUnit.DAYS) // 10 weeks
        val recentWeek = NOW.minus(7, ChronoUnit.DAYS)
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        performanceRepo.seed(listOf(performance(benchId, recentWeek, 100.0)))
        val trainingLog = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").copy(startedAt = firstSetAt)
        val set = WorkoutLoggingFixtures.loggedSet(id = "set-1", sessionId = session.id).copy(
            liftId = benchId,
            completedAt = firstSetAt,
        )
        trainingLog.replaceAll(listOf(session), listOf(set))
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            trainingLog = trainingLog,
            programLiftIds = setOf(benchId),
        )

        val bench = useCase(now = NOW)!!.lifts.first { it.liftId == benchId }
        assertEquals(10, bench.tenureWeeks)
    }

    @Test
    fun `regressing lifts surface first in sorting`() = runTest {
        val goodId = LiftId("good")
        val badId = LiftId("bad")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                lift(goodId, "Good Lift", MuscleGroup.Quad),
                lift(badId, "Bad Lift", MuscleGroup.Back),
            ),
        )
        val performanceRepo = FakeLiftWeeklyPerformanceRepository()
        val w0 = NOW.minus(28, ChronoUnit.DAYS)
        val w1 = NOW.minus(21, ChronoUnit.DAYS)
        val w2 = NOW.minus(14, ChronoUnit.DAYS)
        // Good: improving (100 → 110). Bad: regressing (100 → 90).
        performanceRepo.seed(
            listOf(
                performance(goodId, w0, 100.0), performance(goodId, w1, 105.0), performance(goodId, w2, 110.0),
                performance(badId, w0, 100.0), performance(badId, w1, 95.0), performance(badId, w2, 90.0),
            ),
        )
        val useCase = buildUseCase(
            performanceRepo = performanceRepo,
            liftRepo = liftRepo,
            programLiftIds = setOf(goodId, badId),
        )

        val summary = useCase(now = NOW)!!
        assertEquals(badId, summary.lifts.first().liftId, "regressing lift should sort first")
    }

    private fun buildUseCase(
        stimulusRepo: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
        performanceRepo: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        liftRepo: FakeLiftRepository = FakeLiftRepository(),
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
        programLiftIds: Set<LiftId> = emptySet(),
    ): LoadDashboardUseCase {
        val baseProgram = WorkoutLoggingFixtures.twoWorkoutProgram()
        val program = if (programLiftIds.isEmpty()) {
            baseProgram
        } else {
            // Put all program lift IDs into the first workout so they resolve as active-program lifts.
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
        return LoadDashboardUseCase(
            muscleWeeklyStimulusRepository = stimulusRepo,
            liftWeeklyPerformanceRepository = performanceRepo,
            liftRepository = liftRepo,
            trainingLogRepository = trainingLog,
            programRepository = FakeProgramRepository(program),
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

    private fun performance(liftId: LiftId, weekStart: Instant, peakE1rm: Double): LiftWeeklyPerformance =
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

    private companion object {
        val NOW = Instant.parse("2025-06-01T00:00:00Z")
    }
}

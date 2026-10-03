package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetWorkoutSummaryUseCaseTest {

    @Test
    @DisplayName("Summary includes workout name from program")
    fun summaryIncludesWorkoutName() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val sessionId = TrainingSessionId("ts-1")
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(logRepo, programRepo, ObserveLiftCatalogUseCase(liftRepo), FakeLoggingPort())

        val summary = useCase(sessionId).getOrThrow()

        assertEquals("Workout A", summary.workoutName)
    }

    @Test
    @DisplayName("Missing session fails without non-fatal")
    fun missingSessionFailsWithoutNonFatal() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val logging = FakeLoggingPort()
        val useCase = GetWorkoutSummaryUseCase(logRepo, programRepo, ObserveLiftCatalogUseCase(liftRepo), logging)

        val result = useCase(TrainingSessionId("missing"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
    }

    @Test
    @DisplayName("Duration is computed from startedAt and endedAt")
    fun durationComputed() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(logRepo, programRepo, ObserveLiftCatalogUseCase(liftRepo), FakeLoggingPort())

        val summary = useCase(TrainingSessionId("ts-1")).getOrThrow()

        // Fixture: startedAt 10:00, endedAt 11:00 → 3600 seconds = 3,600,000 millis
        assertEquals(3_600_000L, summary.durationMillis)
    }

    @Test
    @DisplayName("Best set per lift is the highest weight × reps")
    fun bestSetPerLift() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(logRepo, programRepo, ObserveLiftCatalogUseCase(liftRepo), FakeLoggingPort())

        // Add two logged sets for bench: 135×5 and 185×3
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:05:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            ),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(185.0, Weight.WeightUnit.Lb),
                reps = 3,
                rir = 1,
                completedAt = Instant.parse("2025-01-01T10:15:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            ),
        )

        val summary = useCase(TrainingSessionId("ts-1")).getOrThrow()

        assertEquals(1, summary.bestSets.size)
        val best = summary.bestSets[0]
        assertEquals(185.0, best.weight.amount)
        assertEquals(3, best.reps)
    }

    @Test
    @DisplayName("Bodyweight best set is max reps and copies isPersonalRecord")
    fun bodyweightBestSetIsMaxRepsAndCopiesPr() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(
            logRepo,
            programRepo,
            ObserveLiftCatalogUseCase(liftRepo),
            FakeLoggingPort(),
        )
        logRepo.insertSet(bodyweightSet("set-8", reps = 8, setIndex = 0, isPr = false))
        logRepo.insertSet(bodyweightSet("set-12", reps = 12, setIndex = 1, isPr = true))
        logRepo.insertSet(bodyweightSet("set-6", reps = 6, setIndex = 2, isPr = false))

        val summary = useCase(TrainingSessionId("ts-1")).getOrThrow()

        assertEquals(1, summary.bestSets.size)
        val best = summary.bestSets.single()
        assertEquals(12, best.reps)
        assertEquals(0.0, best.weight.amount)
        assertTrue(best.isPersonalRecord)
        assertNull(best.estimatedOneRm)
    }

    @Test
    @DisplayName("Mixed session best set is the loaded working set, not a bodyweight backoff")
    fun mixedSessionBestSetIsLoaded() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(
            logRepo,
            programRepo,
            ObserveLiftCatalogUseCase(liftRepo),
            FakeLoggingPort(),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("work"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(45.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 0,
                completedAt = Instant.parse("2025-01-01T10:05:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                isPersonalRecord = true,
            ),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("bw"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(0.0, Weight.WeightUnit.Lb),
                reps = 12,
                rir = null,
                completedAt = Instant.parse("2025-01-01T10:15:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                isPersonalRecord = false,
            ),
        )

        val summary = useCase(TrainingSessionId("ts-1")).getOrThrow()
        val best = summary.bestSets.single()
        assertEquals(45.0, best.weight.amount)
        assertEquals(5, best.reps)
        assertTrue(best.isPersonalRecord)
    }

    @Test
    @DisplayName("Best-set partialReps keep positives and drop zero")
    fun bestSetPartialRepsNormalized() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-1")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val useCase = GetWorkoutSummaryUseCase(
            logRepo,
            programRepo,
            ObserveLiftCatalogUseCase(liftRepo),
            FakeLoggingPort(),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(185.0, Weight.WeightUnit.Lb),
                reps = 3,
                rir = 1,
                completedAt = Instant.parse("2025-01-01T10:15:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                partialReps = 0,
            ),
        )

        val zeroSummary = useCase(TrainingSessionId("ts-1")).getOrThrow()
        assertNull(zeroSummary.bestSets.single().partialReps)

        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("ts-1"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 1,
                weight = Weight(225.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 1,
                completedAt = Instant.parse("2025-01-01T10:20:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
                partialReps = 2,
            ),
        )

        val withPartials = useCase(TrainingSessionId("ts-1")).getOrThrow()
        assertEquals(2, withPartials.bestSets.single().partialReps)
    }

    @Test
    @DisplayName("Best sets follow program workout-lift order, not alphabetical name")
    fun bestSetsFollowWorkoutLiftOrder() = runTest {
        // Squat (Z name) is position 0; Bench (A name) is position 1 — reverse alpha.
        val squatWlId = WorkoutLiftId("wl-squat")
        val benchWlId = WorkoutLiftId("wl-bench")
        val program = programWithOrderedLifts(
            WorkoutLift(
                id = squatWlId,
                workoutId = WorkoutLoggingFixtures.workoutAId,
                liftId = WorkoutLoggingFixtures.liftSquatId,
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                sets = emptyList(),
            ),
            WorkoutLift(
                id = benchWlId,
                workoutId = WorkoutLoggingFixtures.workoutAId,
                liftId = WorkoutLoggingFixtures.liftBenchId,
                progressionScheme = ProgressionScheme.Double,
                position = 1,
                sets = emptyList(),
            ),
        )
        val programRepo = FakeProgramRepository(program)
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-order")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        // Insert bench first (would sort first alphabetically and by completedAt).
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-bench"),
                sessionId = TrainingSessionId("ts-order"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:05:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext().copy(
                    liftName = "Barbell Bench Press",
                    workoutLiftId = benchWlId,
                ),
            ),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-squat"),
                sessionId = TrainingSessionId("ts-order"),
                exerciseNameRaw = "Barbell Squat",
                liftId = WorkoutLoggingFixtures.liftSquatId,
                setIndex = 0,
                weight = Weight(225.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:20:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext().copy(
                    liftName = "Barbell Squat",
                    workoutLiftId = squatWlId,
                ),
            ),
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift(), squatLift()))
        val useCase = GetWorkoutSummaryUseCase(
            logRepo,
            programRepo,
            ObserveLiftCatalogUseCase(liftRepo),
            FakeLoggingPort(),
        )

        val summary = useCase(TrainingSessionId("ts-order")).getOrThrow()

        assertEquals(2, summary.bestSets.size)
        assertEquals("Barbell Squat", summary.bestSets[0].liftName)
        assertEquals("Barbell Bench Press", summary.bestSets[1].liftName)
    }

    @Test
    @DisplayName("Orphan lifts (no matching workout-lift) follow program lifts chronologically")
    fun orphanLiftsAppendAfterProgramOrder() = runTest {
        val squatWlId = WorkoutLiftId("wl-squat")
        val program = programWithOrderedLifts(
            WorkoutLift(
                id = squatWlId,
                workoutId = WorkoutLoggingFixtures.workoutAId,
                liftId = WorkoutLoggingFixtures.liftSquatId,
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                sets = emptyList(),
            ),
        )
        val programRepo = FakeProgramRepository(program)
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-orphan")
        val logRepo = FakeTrainingLogRepository(
            initialSessions = listOf(session),
            programRepository = programRepo,
        )
        // Orphan (no workoutLiftId) completed first; program squat completed later.
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-bench-orphan"),
                sessionId = TrainingSessionId("ts-orphan"),
                exerciseNameRaw = "Barbell Bench Press",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:05:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext().copy(
                    liftName = "Barbell Bench Press",
                    workoutLiftId = null,
                ),
            ),
        )
        logRepo.insertSet(
            LoggedSet(
                id = LoggedSetId("set-squat"),
                sessionId = TrainingSessionId("ts-orphan"),
                exerciseNameRaw = "Barbell Squat",
                liftId = WorkoutLoggingFixtures.liftSquatId,
                setIndex = 0,
                weight = Weight(225.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:20:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext().copy(
                    liftName = "Barbell Squat",
                    workoutLiftId = squatWlId,
                ),
            ),
        )
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift(), squatLift()))
        val useCase = GetWorkoutSummaryUseCase(
            logRepo,
            programRepo,
            ObserveLiftCatalogUseCase(liftRepo),
            FakeLoggingPort(),
        )

        val summary = useCase(TrainingSessionId("ts-orphan")).getOrThrow()

        assertEquals(2, summary.bestSets.size)
        assertEquals("Barbell Squat", summary.bestSets[0].liftName)
        assertEquals("Barbell Bench Press", summary.bestSets[1].liftName)
    }

    private fun bodyweightSet(
        id: String,
        reps: Int,
        setIndex: Int,
        isPr: Boolean,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId("ts-1"),
            exerciseNameRaw = "Barbell Bench Press",
            liftId = WorkoutLoggingFixtures.liftBenchId,
            setIndex = setIndex,
            weight = Weight(0.0, Weight.WeightUnit.Lb),
            reps = reps,
            rir = null,
            completedAt = Instant.parse("2025-01-01T10:05:00Z").plusSeconds(setIndex.toLong()),
            liftContext = WorkoutLoggingFixtures.importLiftContext(),
            isPersonalRecord = isPr,
        )

    private fun programWithOrderedLifts(vararg lifts: WorkoutLift): Program {
        val base = WorkoutLoggingFixtures.twoWorkoutProgram()
        val workoutA = base.workouts.first { it.id == WorkoutLoggingFixtures.workoutAId }
            .copy(workoutLifts = lifts.toList())
        return base.copy(
            workouts = base.workouts.map { if (it.id == workoutA.id) workoutA else it },
        )
    }

    private fun benchLift(): Lift = Lift(
        id = WorkoutLoggingFixtures.liftBenchId,
        name = "Barbell Bench Press",
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private fun squatLift(): Lift = Lift(
        id = WorkoutLoggingFixtures.liftSquatId,
        name = "Barbell Squat",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )
}

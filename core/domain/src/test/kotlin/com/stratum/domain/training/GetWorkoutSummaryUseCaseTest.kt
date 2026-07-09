package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
        assertEquals(185.0, best.weight)
        assertEquals(3, best.reps)
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
}

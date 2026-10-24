package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseModelingSchedulerPort
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.withStart
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RebuildLocalDerivedTrainingDataUseCaseTest {
    private val persistence = FakeResponseProfilePersistencePort()
    private val modelingScheduler = FakeResponseModelingSchedulerPort()

    @Test
    @DisplayName("rebuilds weekly series from hydrated training logs and enqueues modeling")
    fun rebuildsWeeklyAndEnqueuesModeling() = runTest {
        val liftId = LiftId("bench")
        val liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(listOf(lift(liftId, MuscleGroup.Chest)))
        val weekStart = Instant.parse("2025-05-05T00:00:00Z")
        val logRepo = FakeTrainingLogRepository()
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "s1").withStart(weekStart)
        logRepo.seedSession(session)
        logRepo.seedSet(
            LoggedSet(
                id = LoggedSetId("set-1"),
                sessionId = session.id,
                exerciseNameRaw = "Bench",
                liftId = liftId,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Kg),
                reps = 5,
                rir = 2,
                completedAt = weekStart,
                liftContext = LoggedLiftContext(liftId.value),
            ),
        )

        val result = buildUseCase(logRepo, liftRepo).invoke()

        assertTrue(result.isSuccess)
        assertTrue(
            persistence.weeklyStimulusRepository.getAll().isNotEmpty(),
            "hydrated sets must produce weekly stimulus for the dashboard",
        )
        assertTrue(
            persistence.liftPerformanceRepository.getAll().isNotEmpty(),
            "hydrated sets must produce lift weekly performance for the dashboard",
        )
        assertEquals(1, modelingScheduler.enqueueCount)
    }

    @Test
    @DisplayName("empty history succeeds and still enqueues modeling (no-op weekly)")
    fun emptyHistoryStillEnqueues() = runTest {
        val result = buildUseCase(FakeTrainingLogRepository(), FakeLiftRepository()).invoke()

        assertTrue(result.isSuccess)
        assertTrue(persistence.weeklyStimulusRepository.getAll().isEmpty())
        assertEquals(1, modelingScheduler.enqueueCount)
    }

    private fun buildUseCase(
        logRepo: FakeTrainingLogRepository,
        liftRepo: FakeLiftRepository,
    ): RebuildLocalDerivedTrainingDataUseCase =
        RebuildLocalDerivedTrainingDataUseCase(
            localDerivedStages =
                com.stratum.core.testing.training.localDerivedTrainingStages(
                    trainingLog = logRepo,
                    liftRepository = liftRepo,
                    persistence = persistence,
                ),
            responseModelingScheduler = modelingScheduler,
            loggingPort = FakeLoggingPort(),
        )

    private fun lift(id: LiftId, muscle: MuscleGroup): Lift =
        Lift(
            id = id,
            name = id.value,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(muscle),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}

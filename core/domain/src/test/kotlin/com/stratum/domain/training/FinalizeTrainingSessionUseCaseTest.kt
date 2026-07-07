package com.stratum.domain.training

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FinalizeTrainingSessionUseCaseTest {
    @Test
    @DisplayName("WL-023 FinalizeTrainingSessionUseCase marks session COMPLETED")
    fun wl023_finalizeMarksCompleted() = runTest {
        val useCase = buildUseCase()

        val result = useCase(TrainingSessionId("ts-live-1"))

        val session = result.getOrThrow()
        assertEquals(TrainingSessionStatus.COMPLETED, session.status)
        assertNotNull(session.endedAt)
    }

    @Test
    @DisplayName("WL-024 Finalize program workout increments cycle_position cache")
    fun wl024_finalizeAdvancesCyclePosition() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(1, programRepo.getProgram()?.cyclePosition)
    }

    @Test
    @DisplayName("WL-025 Finalize AD_HOC session does not change cycle pointer")
    fun wl025_adHocFinalizeNoAdvance() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram(cyclePosition = 0))
        val useCase = buildUseCase(programRepo)

        useCase(TrainingSessionId("ts-adhoc")).getOrThrow()

        assertEquals(0, programRepo.getProgram()?.cyclePosition)
        assertEquals(1, programRepo.getProgram()?.currentCycleNumber)
    }

    @Test
    @DisplayName("WL-039 Finalizing a single workout does not append program_change_events")
    fun wl039_finalizeDoesNotEmitChangeEvent() = runTest {
        val events = FakeProgramChangeEventRepository()
        val useCase = buildUseCase(eventRepository = events)

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertEquals(0, events.getEventsForProgram(WorkoutLoggingFixtures.programId).size)
    }

    @Test
    @DisplayName("Finalize updates sets in session with computed e1RMs and PRs")
    fun finalizeUpdatesSetsWithPrs() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val events = FakeProgramChangeEventRepository()
        val repo = FakeTrainingLogRepository(programRepository = programRepo, programChangeEventRepository = events)
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        
        val liftId = com.stratum.domain.lift.identity.LiftId("lift-1")
        val set1 = LoggedSet(
            id = LoggedSetId("set-1"),
            sessionId = TrainingSessionId("ts-live-1"),
            exerciseNameRaw = "Squat",
            liftId = liftId,
            setIndex = 1,
            weight = com.stratum.domain.lift.Weight(100.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
            reps = 5,
            rir = null,
            completedAt = Instant.now(),
            liftContext = LoggedLiftContext("Squat")
        )
        repo.seedSet(set1)

        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        val useCase = FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords
        )

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        val updatedSets = repo.getSetsForSession(TrainingSessionId("ts-live-1"))
        assertEquals(1, updatedSets.size)
        val updatedSet = updatedSets.first()
        assertTrue(updatedSet.isPersonalRecord)
        assertEquals(100.0 * (1.0278 / (1.0278 - 0.0278 * 5)), updatedSet.estimatedOneRm!!, 0.01)
    }

    private suspend fun buildUseCase(
        programRepo: FakeProgramRepository = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram()),
        eventRepository: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
    ): FinalizeTrainingSessionUseCase {
        val repo =
            FakeTrainingLogRepository(
                programRepository = programRepo,
                programChangeEventRepository = eventRepository,
            )
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.seedSession(
            WorkoutLoggingFixtures.inProgressSession(
                id = "ts-adhoc",
                workoutId = null,
                kind = TrainingSessionKind.AD_HOC,
            ),
        )
        val computePersonalRecords = ComputePersonalRecordsUseCase(repo)
        return FinalizeTrainingSessionUseCase(
            trainingLogRepository = repo,
            programRepository = programRepo,
            computePersonalRecords = computePersonalRecords,
        )
    }
}

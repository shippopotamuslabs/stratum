package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.toReorderDayItems

class ReorderProgramCycleDaysUseCaseTest {

    @Test
    @DisplayName("persist reorder remaps current-cycle rest snapshots and reconciles cyclePosition")
    fun invoke_remapsRestSnapshotsAndPointer() = runTest {
        val program = sampleProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingRepo = FakeTrainingLogRepository()
        // Rest at slot 1 completed this cycle.
        trainingRepo.seedSession(
            restSession(id = "rest-1", slotIndex = 1, cycle = program.currentCycleNumber),
        )
        // Training A completed at slot 0.
        trainingRepo.seedSession(
            trainSession(id = "train-a", workoutId = "w-a", slotIndex = 0, cycle = program.currentCycleNumber),
        )

        val useCase =
            ReorderProgramCycleDaysUseCase(
                programRepository = programRepo,
                trainingLogRepository = trainingRepo,
                loggingPort = FakeLoggingPort(),
            )
        val items = program.toReorderDayItems()
        // Rest, A, B, Rest → rest that was@1 moves to 0
        val ordered =
            listOf(
                items[1].dayId,
                items[0].dayId,
                items[2].dayId,
                items[3].dayId,
            )

        val result = useCase(ProgramId("prog-1"), ordered)
        assertTrue(result.isSuccess)

        val rest = trainingRepo.getSession(TrainingSessionId("rest-1"))!!
        assertEquals(0, rest.cycleSlotIndexSnapshot)

        val train = trainingRepo.getSession(TrainingSessionId("train-a"))!!
        assertEquals(1, train.cycleSlotIndexSnapshot)

        val saved = programRepo.getProgramById(ProgramId("prog-1"))!!
        assertEquals(setOf(0, 3), saved.restSlotIndices)
        assertEquals(listOf("w-a", "w-b"), saved.workouts.map { it.id.value })
        // Ledger: training A + rest consumed at new indices → next unconsumed is B@2
        assertEquals(2, saved.cyclePosition)
    }

    @Test
    @DisplayName("persist reorder does not rewrite prior-cycle rest snapshots")
    fun invoke_skipsPriorCycleSessions() = runTest {
        val program = sampleProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingRepo = FakeTrainingLogRepository()
        trainingRepo.seedSession(
            restSession(id = "old-rest", slotIndex = 1, cycle = program.currentCycleNumber - 1),
        )
        val useCase =
            ReorderProgramCycleDaysUseCase(
                programRepository = programRepo,
                trainingLogRepository = trainingRepo,
                loggingPort = FakeLoggingPort(),
            )
        val items = program.toReorderDayItems()
        val ordered =
            listOf(
                items[1].dayId,
                items[0].dayId,
                items[2].dayId,
                items[3].dayId,
            )

        assertTrue(useCase(ProgramId("prog-1"), ordered).isSuccess)
        assertEquals(1, trainingRepo.getSession(TrainingSessionId("old-rest"))!!.cycleSlotIndexSnapshot)
    }

    @Test
    @DisplayName("persist reorder failure surfaces when program missing")
    fun invoke_missingProgramFails() = runTest {
        val useCase =
            ReorderProgramCycleDaysUseCase(
                programRepository = FakeProgramRepository(),
                trainingLogRepository = FakeTrainingLogRepository(),
                loggingPort = FakeLoggingPort(),
            )
        assertTrue(useCase(ProgramId("missing"), listOf("a")).isFailure)
    }

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId("prog-1"),
            name = "Sample",
            createdAt = Instant.EPOCH,
            cycleLength = 4,
            workouts =
                listOf(
                    workout("w-a", 0, "A"),
                    workout("w-b", 1, "B"),
                ),
            restSlotIndices = setOf(1, 3),
            cyclePosition = 0,
            currentCycleNumber = 1,
            isActive = true,
        )

    private fun workout(id: String, position: Int, name: String): Workout =
        Workout(
            id = WorkoutId(id),
            programId = ProgramId("prog-1"),
            name = name,
            weekday = null,
            position = position,
            workoutLifts = emptyList(),
        )

    private fun restSession(id: String, slotIndex: Int, cycle: Int): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = ProgramId("prog-1"),
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.REST_DAY,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = cycle,
            weekdaySnapshot = null,
            cycleSlotIndexSnapshot = slotIndex,
            startedAt = Instant.EPOCH,
            endedAt = Instant.EPOCH,
            sourceApp = null,
        )

    private fun trainSession(
        id: String,
        workoutId: String,
        slotIndex: Int,
        cycle: Int,
    ): TrainingSession =
        TrainingSession(
            id = TrainingSessionId(id),
            programId = ProgramId("prog-1"),
            workoutId = WorkoutId(workoutId),
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = cycle,
            weekdaySnapshot = null,
            cycleSlotIndexSnapshot = slotIndex,
            startedAt = Instant.EPOCH,
            endedAt = Instant.EPOCH,
            sourceApp = null,
        )
}

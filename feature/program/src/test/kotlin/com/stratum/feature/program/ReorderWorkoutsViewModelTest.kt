package com.stratum.feature.program

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.program.Program
import com.stratum.application.program.ReorderProgramCycleDaysUseCase
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.feature.program.ui.ReorderWorkoutsViewModel
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ReorderWorkoutsViewModelTest {

    private lateinit var programRepository: FakeProgramRepository
    private lateinit var viewModel: ReorderWorkoutsViewModel

    @BeforeEach
    fun setUp() {
        programRepository = FakeProgramRepository(sampleProgram())
        viewModel =
            ReorderWorkoutsViewModel(
                reorderProgramCycleDays =
                    ReorderProgramCycleDaysUseCase(
                        programRepository = programRepository,
                        trainingLogRepository = FakeTrainingLogRepository(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )
    }

    @Test
    @DisplayName("Given load when days ready then isLoaded and list match program slots")
    fun initialize_loadsDays() = runTest {
        viewModel.initialize(PROGRAM_ID)
        advanceUntilIdle()

        viewModel.days.test {
            val days = awaitItem()
            assertEquals(listOf("w-a", "rest-1-prog-1", "w-b", "rest-3-prog-1"), days.map { it.dayId })
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(viewModel.isLoaded.value)
    }

    @Test
    @DisplayName("Given loaded days when confirm succeeds then onDone runs and program is saved")
    fun confirm_successPops() = runTest {
        viewModel.initialize(PROGRAM_ID)
        advanceUntilIdle()
        viewModel.reorderDays(fromIndex = 0, toIndex = 1)

        var done = false
        viewModel.confirmReorder { done = true }
        advanceUntilIdle()

        assertTrue(done)
        assertNull(viewModel.userMessage.value)
        val saved = programRepository.getProgramById(ProgramId(PROGRAM_ID))!!
        assertEquals(setOf(0, 3), saved.restSlotIndices)
        assertEquals(listOf("w-a", "w-b"), saved.workouts.map { it.id.value })
    }

    @Test
    @DisplayName("Given not loaded when confirm then onDone does not run")
    fun confirm_beforeLoadNoOp() = runTest {
        // Do not advance idle — still loading or empty.
        var done = false
        viewModel.confirmReorder { done = true }
        advanceUntilIdle()
        assertFalse(done)
        assertTrue(programRepository.saveProgramCalls.isEmpty())
    }

    @Test
    @DisplayName("Given save failure when confirm then onDone does not run and userMessage set")
    fun confirm_failureKeepsScreen() = runTest {
        programRepository = FakeProgramRepository(sampleProgram(), failOnSave = true)
        viewModel =
            ReorderWorkoutsViewModel(
                reorderProgramCycleDays =
                    ReorderProgramCycleDaysUseCase(
                        programRepository = programRepository,
                        trainingLogRepository = FakeTrainingLogRepository(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )
        viewModel.initialize(PROGRAM_ID)
        advanceUntilIdle()
        // Force a real mutation so save is attempted.
        viewModel.reorderDays(0, 1)

        var done = false
        viewModel.confirmReorder { done = true }
        advanceUntilIdle()

        assertFalse(done)
        assertEquals("Couldn't reorder workouts. Try again.", viewModel.userMessage.value)
    }

    @Test
    @DisplayName("Given empty program load when confirm then onDone does not run")
    fun confirm_emptyDaysNoOp() = runTest {
        programRepository = FakeProgramRepository()
        viewModel =
            ReorderWorkoutsViewModel(
                reorderProgramCycleDays =
                    ReorderProgramCycleDaysUseCase(
                        programRepository = programRepository,
                        trainingLogRepository = FakeTrainingLogRepository(),
                        loggingPort = FakeLoggingPort(),
                    ),
            )
        viewModel.initialize("missing")
        advanceUntilIdle()
        assertTrue(viewModel.isLoaded.value)
        assertTrue(viewModel.days.value.isEmpty())

        var done = false
        viewModel.confirmReorder { done = true }
        advanceUntilIdle()
        assertFalse(done)
    }

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId(PROGRAM_ID),
            name = "Sample",
            createdAt = Instant.EPOCH,
            cycleLength = 4,
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId("w-a"),
                        programId = ProgramId(PROGRAM_ID),
                        name = "A",
                        weekday = null,
                        position = 0,
                        workoutLifts = emptyList(),
                    ),
                    Workout(
                        id = WorkoutId("w-b"),
                        programId = ProgramId(PROGRAM_ID),
                        name = "B",
                        weekday = null,
                        position = 1,
                        workoutLifts = emptyList(),
                    ),
                ),
            restSlotIndices = setOf(1, 3),
            cyclePosition = 0,
            isActive = true,
        )

    private companion object {
        const val PROGRAM_ID = "prog-1"
    }
}

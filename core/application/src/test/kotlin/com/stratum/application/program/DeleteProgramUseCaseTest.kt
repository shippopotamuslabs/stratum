package com.stratum.application.program

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.program.identity.ProgramId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DeleteProgramUseCaseTest {
    @Test
    @DisplayName("Deletes a program from the repository when no active session")
    fun deletesProgram() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository()
        val useCase =
            DeleteProgramUseCase(programRepo, trainingLog)

        val result = useCase(program.id)

        assertEquals(DeleteProgramResult.Success, result)
        assertNull(programRepo.getProgramById(program.id))
    }

    @Test
    @DisplayName("Refuses delete while an in-progress session is linked to the program")
    fun refusesDeleteWithActiveSession() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository()
        trainingLog.seedSession(WorkoutLoggingFixtures.inProgressSession())
        val useCase =
            DeleteProgramUseCase(programRepo, trainingLog)

        val result = useCase(program.id)

        assertEquals(DeleteProgramResult.SessionInProgress(program.id), result)
        assertTrue(programRepo.getProgramById(program.id) != null)
    }

    @Test
    @DisplayName("Refuses to delete import-history")
    fun refusesImportHistory() = runTest {
        val programRepo = FakeProgramRepository(null)
        val trainingLog = FakeTrainingLogRepository()
        val useCase =
            DeleteProgramUseCase(programRepo, trainingLog)

        val result = useCase(ProgramId.ImportHistory)

        assertEquals(DeleteProgramResult.ImportHistoryProgram(ProgramId.ImportHistory), result)
    }
}

package com.stratum.domain.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class DiscardActiveProgramUseCaseTest {
    @Test
    @DisplayName("Deletes the active program when present")
    fun deletesActiveProgram() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository()
        val delete = DeleteProgramUseCase(programRepo, trainingLog, FakeLoggingPort())
        val subject = DiscardActiveProgramUseCase(programRepo, delete, FakeLoggingPort())

        val result = subject()

        assertTrue(result.isSuccess)
        assertNull(programRepo.getProgram())
        assertNull(programRepo.getProgramById(program.id))
    }

    @Test
    @DisplayName("Succeeds with no work when there is no active program")
    fun noOpWithoutActiveProgram() = runTest {
        val programRepo = FakeProgramRepository(null)
        val trainingLog = FakeTrainingLogRepository()
        val delete = DeleteProgramUseCase(programRepo, trainingLog, FakeLoggingPort())
        val subject = DiscardActiveProgramUseCase(programRepo, delete, FakeLoggingPort())

        val result = subject()

        assertTrue(result.isSuccess)
    }

    @Test
    @DisplayName("Propagates failure if active program cannot be deleted")
    fun propagatesDeleteFailure() = runTest {
        val program = WorkoutLoggingFixtures.twoWorkoutProgram()
        val programRepo = FakeProgramRepository(program)
        val trainingLog = FakeTrainingLogRepository().apply {
            seedSession(WorkoutLoggingFixtures.inProgressSession())
        }
        val delete = DeleteProgramUseCase(programRepo, trainingLog, FakeLoggingPort())
        val subject = DiscardActiveProgramUseCase(programRepo, delete, FakeLoggingPort())

        val result = subject()

        assertTrue(result.isFailure)
    }
}

package com.stratum.application.training

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetCompletedSessionForEditUseCaseTest {

    @Test
    @DisplayName("Given missing session when loaded for edit then failure branch")
    fun missingSessionFailsWithoutNonFatal() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val logRepo = FakeTrainingLogRepository(programRepository = programRepo)
        val useCase = forEditUseCase(logRepo, programRepo)

        val result = useCase(TrainingSessionId("missing"))

        assertEquals(
            GetCompletedSessionForEditResult.SessionNotFound(TrainingSessionId("missing")),
            result,
        )
    }

    @Test
    @DisplayName("Given in-progress session when loaded for edit then failure branch")
    fun inProgressSessionFailsWithoutNonFatal() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.inProgressSession(id = "ts-ip")
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                programRepository = programRepo,
            )
        val useCase = forEditUseCase(logRepo, programRepo)

        val result = useCase(TrainingSessionId("ts-ip"))

        assertEquals(
            GetCompletedSessionForEditResult.SessionNotCompleted(
                TrainingSessionId("ts-ip"),
                com.stratum.domain.training.TrainingSessionStatus.IN_PROGRESS,
            ),
            result,
        )
    }

    @Test
    @DisplayName("Given completed session with no sets when loaded for edit then NoLoggedSets branch")
    fun noLoggedSetsFailsWithoutNonFatal() = runTest {
        val programRepo = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val session = WorkoutLoggingFixtures.completedLiveSession(id = "ts-empty")
        val logRepo =
            FakeTrainingLogRepository(
                initialSessions = listOf(session),
                programRepository = programRepo,
            )
        val useCase = forEditUseCase(logRepo, programRepo)

        val result = useCase(TrainingSessionId("ts-empty"))

        assertEquals(
            GetCompletedSessionForEditResult.NoLoggedSets(TrainingSessionId("ts-empty")),
            result,
        )
    }

    private fun forEditUseCase(
        logRepo: FakeTrainingLogRepository,
        programRepo: FakeProgramRepository,
    ) = GetCompletedSessionForEditUseCase(
        trainingLogRepository = logRepo,
        programRepository = programRepo,
        liftRepository = FakeLiftRepository(),
    )
}

package com.stratum.application.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AbandonTrainingSessionUseCaseTest {
    @Test
    @DisplayName("WL-026 AbandonTrainingSessionUseCase deletes session and sets")
    fun wl026_abandonDeletesSessionAndSets() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.seedSet(
            WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = TrainingSessionId("ts-live-1"),
            ),
        )
        val useCase = AbandonTrainingSessionUseCase(repo, FakeLoggingPort())

        assertEquals(AbandonTrainingSessionResult.Success, useCase(TrainingSessionId("ts-live-1")))

        assertNull(repo.getSession(TrainingSessionId("ts-live-1")))
        assertTrue(repo.getSetsForSession(TrainingSessionId("ts-live-1")).isEmpty())
    }

    @Test
    @DisplayName("Delete failure is returned as PersistenceFailed and logged")
    fun deleteFailureIsReturned() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        repo.deleteSessionError = IllegalStateException("delete boom")
        val logging = FakeLoggingPort()
        val useCase = AbandonTrainingSessionUseCase(repo, logging)

        val result = useCase(TrainingSessionId("ts-live-1"))

        assertEquals(AbandonTrainingSessionResult.PersistenceFailed, result)
        assertEquals("Failed to abandon training session: delete boom", logging.errors.single().message)
        assertEquals("AbandonTrainingSession", logging.errors.single().tag)
        assertNotNull(repo.getSession(TrainingSessionId("ts-live-1")))
    }
}

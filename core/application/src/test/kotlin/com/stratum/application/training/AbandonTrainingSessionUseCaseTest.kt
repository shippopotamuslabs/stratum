package com.stratum.application.training

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
        val useCase = AbandonTrainingSessionUseCase(repo)

        assertEquals(AbandonTrainingSessionResult.Success, useCase(TrainingSessionId("ts-live-1")))

        assertNull(repo.getSession(TrainingSessionId("ts-live-1")))
        assertTrue(repo.getSetsForSession(TrainingSessionId("ts-live-1")).isEmpty())
    }

    @Test
    @DisplayName("Delete failure is returned as PersistenceFailed carrying the record")
    fun deleteFailureIsReturned() = runTest {
        val repo = FakeTrainingLogRepository()
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = "ts-live-1"))
        val failure = IllegalStateException("delete boom")
        repo.deleteSessionError = failure
        val useCase = AbandonTrainingSessionUseCase(repo)

        val result = useCase(TrainingSessionId("ts-live-1"))

        val failed = result as AbandonTrainingSessionResult.PersistenceFailed
        val diagnostic = failed.diagnostics.single()
        assertEquals("Failed to abandon training session: delete boom", diagnostic.message)
        assertEquals("AbandonTrainingSession", diagnostic.tag)
        org.junit.jupiter.api.Assertions.assertSame(failure, diagnostic.cause)
        assertNotNull(repo.getSession(TrainingSessionId("ts-live-1")))
    }
}

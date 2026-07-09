package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
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

        useCase(TrainingSessionId("ts-live-1")).getOrThrow()

        assertNull(repo.getSession(TrainingSessionId("ts-live-1")))
        assertTrue(repo.getSetsForSession(TrainingSessionId("ts-live-1")).isEmpty())
    }
}

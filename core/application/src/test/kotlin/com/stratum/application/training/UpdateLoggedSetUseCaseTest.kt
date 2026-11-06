package com.stratum.application.training

import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.LoggedSet

class UpdateLoggedSetUseCaseTest {

    @Test
    @DisplayName("updates set for IN_PROGRESS session")
    fun updatesInProgress() = runTest {
        val repo = FakeTrainingLogRepository()
        val sessionId = TrainingSessionId("ts-live-1")
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = sessionId.value))
        val original =
            LoggedSet(
                id = LoggedSetId("ls-1"),
                sessionId = sessionId,
                exerciseNameRaw = "Bench",
                liftId = LiftId("lift-1"),
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2024-01-01T00:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        repo.insertSet(original)

        val useCase = UpdateLoggedSetUseCase(repo)
        val result =
            useCase(
                original.copy(
                    weight = Weight(140.0, Weight.WeightUnit.Lb),
                    reps = 6,
                    rir = 1,
                ),
            )

        assertEquals(UpdateLoggedSetResult.Success, result)
        val updated = repo.getAllSets().single()
        assertEquals(140.0, updated.weight.amount)
        assertEquals(6, updated.reps)
        assertEquals(1, updated.rir)
        assertEquals(LoggedSetId("ls-1"), updated.id)
    }

    @Test
    @DisplayName("fails when session is not IN_PROGRESS")
    fun rejectsCompletedSession() = runTest {
        val repo = FakeTrainingLogRepository()
        val sessionId = TrainingSessionId("ts-done")
        repo.seedSession(WorkoutLoggingFixtures.completedLiveSession(id = sessionId.value))
        val set =
            LoggedSet(
                id = LoggedSetId("ls-1"),
                sessionId = sessionId,
                exerciseNameRaw = "Bench",
                liftId = LiftId("lift-1"),
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2024-01-01T00:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        repo.insertSet(set)

        val useCase = UpdateLoggedSetUseCase(repo)
        val result = useCase(set.copy(reps = 8))

        assertEquals(
            UpdateLoggedSetResult.SessionNotInProgress(
                sessionId,
                com.stratum.domain.training.TrainingSessionStatus.COMPLETED,
            ),
            result,
        )
        assertEquals(5, repo.getAllSets().single().reps)
    }

    @Test
    @DisplayName("operational getSession failure returns PersistenceFailed carrying exactly one diagnostic")
    fun operationalLoadFailureReturnsFailureAndLogsOnce() = runTest {
        val repo = FakeTrainingLogRepository()
        val failure = IllegalStateException("room load failed")
        repo.getSessionError = failure
        val sessionId = TrainingSessionId("ts-live-1")
        val set =
            LoggedSet(
                id = LoggedSetId("ls-1"),
                sessionId = sessionId,
                exerciseNameRaw = "Bench",
                liftId = LiftId("lift-1"),
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = 2,
                completedAt = Instant.parse("2024-01-01T00:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val useCase = UpdateLoggedSetUseCase(repo)

        val result = useCase(set)

        val failed = result as UpdateLoggedSetResult.PersistenceFailed
        val diagnostic = failed.diagnostics.single()
        assertEquals("Failed to update logged set: ${failure.message}", diagnostic.message)
        assertEquals("UpdateLoggedSetUseCase", diagnostic.tag)
        org.junit.jupiter.api.Assertions.assertSame(failure, diagnostic.cause)
    }
}

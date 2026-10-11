package com.stratum.domain.training

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RewriteSessionLiftEquipmentUseCaseTest {

    @Test
    @DisplayName("rewrites equipment on matching sets only, returns rows touched")
    fun rewritesMatchingSets() = runTest {
        val repo = FakeTrainingLogRepository()
        val sessionId = TrainingSessionId("ts-live-1")
        repo.seedSession(WorkoutLoggingFixtures.inProgressSession(id = sessionId.value))
        repo.insertSet(loggedSet("ls-1", sessionId, liftId = "lift-1", equipmentId = "barbell"))
        repo.insertSet(loggedSet("ls-2", sessionId, liftId = "lift-1", equipmentId = "barbell"))
        repo.insertSet(loggedSet("ls-3", sessionId, liftId = "lift-2", equipmentId = "barbell"))
        repo.insertSet(loggedSet("ls-4", sessionId, liftId = "lift-1", equipmentId = "ssb"))

        val useCase = RewriteSessionLiftEquipmentUseCase(repo, FakeLoggingPort())
        val result = useCase(sessionId, LiftId("lift-1"), EquipmentId("ssb"))

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        val byId = repo.getAllSets().associateBy { it.id.value }
        assertEquals(EquipmentId("ssb"), byId.getValue("ls-1").equipmentId)
        assertEquals(EquipmentId("ssb"), byId.getValue("ls-2").equipmentId)
        assertEquals(EquipmentId("barbell"), byId.getValue("ls-3").equipmentId)
        assertEquals(EquipmentId("ssb"), byId.getValue("ls-4").equipmentId)
    }

    @Test
    @DisplayName("fails when session is not IN_PROGRESS, touches nothing")
    fun rejectsCompletedSession() = runTest {
        val repo = FakeTrainingLogRepository()
        val sessionId = TrainingSessionId("ts-done")
        repo.seedSession(WorkoutLoggingFixtures.completedLiveSession(id = sessionId.value))
        repo.insertSet(loggedSet("ls-1", sessionId, liftId = "lift-1", equipmentId = "barbell"))

        val logging = FakeLoggingPort()
        val useCase = RewriteSessionLiftEquipmentUseCase(repo, logging)
        val result = useCase(sessionId, LiftId("lift-1"), EquipmentId("ssb"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TrainingSessionNotInProgressException)
        assertEquals(EquipmentId("barbell"), repo.getAllSets().single().equipmentId)
        assertEquals(1, logging.errors.size)
    }

    private fun loggedSet(
        id: String,
        sessionId: TrainingSessionId,
        liftId: String,
        equipmentId: String,
    ): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Bench",
            liftId = LiftId(liftId),
            equipmentId = EquipmentId(equipmentId),
            setIndex = 0,
            weight = Weight(135.0, Weight.WeightUnit.Lb),
            reps = 5,
            rir = 2,
            completedAt = Instant.parse("2024-01-01T00:00:00Z"),
            liftContext = WorkoutLoggingFixtures.importLiftContext(),
        )
}

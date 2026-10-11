package com.stratum.domain.lift

import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GetRecentlyUsedEquipmentForLiftUseCase")
class GetRecentlyUsedEquipmentForLiftUseCaseTest {

    private val sessionId = TrainingSessionId("ts-1")
    private val stackA = EquipmentId("stack-a")
    private val stackB = EquipmentId("stack-b")
    private val stackTombstoned = EquipmentId("stack-old")

    @Test
    @DisplayName("returns distinct equipment most-recent first")
    fun returnsDistinctMostRecentFirst() = runTest {
        val (trainingRepo, useCase) = setup()
        trainingRepo.seedSet(set("s-1", stackA, "2025-01-01T10:00:00Z"))
        trainingRepo.seedSet(set("s-2", stackB, "2025-01-02T10:00:00Z"))
        trainingRepo.seedSet(set("s-3", stackA, "2025-01-03T10:00:00Z"))

        val recents = useCase(WorkoutLoggingFixtures.liftBenchId)

        assertEquals(listOf(stackA, stackB), recents.map { it.id })
    }

    @Test
    @DisplayName("skips sets without equipment, tombstoned rows, and other lifts")
    fun skipsNullTombstonedAndForeignSets() = runTest {
        val (trainingRepo, useCase) = setup()
        trainingRepo.seedSet(set("s-1", stackTombstoned, "2025-01-05T10:00:00Z"))
        trainingRepo.seedSet(set("s-2", null, "2025-01-04T10:00:00Z"))
        trainingRepo.seedSet(
            set("s-3", stackB, "2025-01-03T10:00:00Z")
                .copy(liftId = WorkoutLoggingFixtures.liftSquatId),
        )
        trainingRepo.seedSet(set("s-4", stackA, "2025-01-01T10:00:00Z"))

        val recents = useCase(WorkoutLoggingFixtures.liftBenchId)

        assertEquals(listOf(stackA), recents.map { it.id })
    }

    @Test
    @DisplayName("honors the limit")
    fun honorsLimit() = runTest {
        val (trainingRepo, useCase) = setup()
        trainingRepo.seedSet(set("s-1", stackB, "2025-01-01T10:00:00Z"))
        trainingRepo.seedSet(set("s-2", stackA, "2025-01-02T10:00:00Z"))

        val recents = useCase(WorkoutLoggingFixtures.liftBenchId, limit = 1)

        assertEquals(listOf(stackA), recents.map { it.id })
    }

    @Test
    @DisplayName("tombstoned ids do not consume limit slots")
    fun tombstonedIdsDoNotConsumeLimit() = runTest {
        val (trainingRepo, useCase) = setup()
        trainingRepo.seedSet(set("s-1", stackTombstoned, "2025-01-05T10:00:00Z"))
        trainingRepo.seedSet(set("s-2", stackA, "2025-01-01T10:00:00Z"))

        val recents = useCase(WorkoutLoggingFixtures.liftBenchId, limit = 1)

        assertEquals(listOf(stackA), recents.map { it.id })
    }

    @Test
    @DisplayName("returns empty when the lift has no equipment history")
    fun emptyWithoutHistory() = runTest {
        val (_, useCase) = setup()

        assertTrue(useCase(WorkoutLoggingFixtures.liftBenchId).isEmpty())
    }

    private fun setup(): Pair<FakeTrainingLogRepository, GetRecentlyUsedEquipmentForLiftUseCase> {
        val trainingRepo = FakeTrainingLogRepository()
        val equipmentRepo =
            FakeEquipmentRepository(
                initial =
                    listOf(
                        Equipment(
                            id = stackA,
                            name = "Stack A",
                            incrementTiers = emptyList(),
                            isCustom = true,
                        ),
                        Equipment(
                            id = stackB,
                            name = "Stack B",
                            incrementTiers = emptyList(),
                            isCustom = true,
                        ),
                        Equipment(
                            id = stackTombstoned,
                            name = "Stack Old",
                            incrementTiers = emptyList(),
                            isCustom = true,
                            isDeleted = true,
                        ),
                    ),
            )
        return trainingRepo to GetRecentlyUsedEquipmentForLiftUseCase(trainingRepo, equipmentRepo)
    }

    private fun set(id: String, equipmentId: EquipmentId?, completedAt: String) =
        WorkoutLoggingFixtures.loggedSet(id = id, sessionId = sessionId, equipmentId = equipmentId)
            .copy(completedAt = Instant.parse(completedAt))
}

package com.stratum.domain.training

import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.identity.TrainingSessionId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ComputePersonalRecordsUseCaseTest {

    private val liftId = LiftId("lift-bench")
    private val sessionId = TrainingSessionId("session-1")

    @Test
    @DisplayName("Compute PRs identifies correct max e1RM set as PR when no existing PR exists")
    fun computePrs_noExistingPr() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("set-1", weight = 100.0, reps = 5),  // e1RM = ~116.7
            createSet("set-2", weight = 110.0, reps = 3),  // e1RM = ~121.0 (max)
            createSet("set-3", weight = 90.0, reps = 8)    // e1RM = ~112.5
        )

        val result = useCase(sets).getOrThrow()

        assertEquals(3, result.updatedIncomingSets.size)
        assertTrue(result.oldPrSetsToUpdate.isEmpty())

        val set1 = result.updatedIncomingSets.first { it.id.value == "set-1" }
        val set2 = result.updatedIncomingSets.first { it.id.value == "set-2" }
        val set3 = result.updatedIncomingSets.first { it.id.value == "set-3" }

        assertFalse(set1.isPersonalRecord)
        assertTrue(set2.isPersonalRecord)
        assertFalse(set3.isPersonalRecord)

        assertEquals(110.0 * (1.0278 / (1.0278 - 0.0278 * 3)), set2.estimatedOneRm!!, 0.01)
    }

    @Test
    @DisplayName("Compute PRs updates old PR if new PR is strictly greater")
    fun computePrs_betterThanExistingPr() = runTest {
        val repo = FakeTrainingLogRepository()
        val oldPrSet = createSet("old-pr", weight = 115.0, reps = 1, isPr = true, e1rm = 115.0)
        repo.seedSet(oldPrSet)

        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("new-set-1", weight = 100.0, reps = 8), // e1RM = ~125.0 (greater)
            createSet("new-set-2", weight = 90.0, reps = 5)    // e1RM = ~105.0
        )

        val result = useCase(sets).getOrThrow()

        // The old PR set should be in the oldPrSetsToUpdate list with isPersonalRecord = false
        assertEquals(1, result.oldPrSetsToUpdate.size)
        assertEquals("old-pr", result.oldPrSetsToUpdate.first().id.value)
        assertFalse(result.oldPrSetsToUpdate.first().isPersonalRecord)

        // The incoming max set should be marked as PR
        val set1 = result.updatedIncomingSets.first { it.id.value == "new-set-1" }
        val set2 = result.updatedIncomingSets.first { it.id.value == "new-set-2" }
        assertTrue(set1.isPersonalRecord)
        assertFalse(set2.isPersonalRecord)
    }

    @Test
    @DisplayName("Compute PRs preserves old PR if new PR is less or equal")
    fun computePrs_worseThanExistingPr() = runTest {
        val repo = FakeTrainingLogRepository()
        val oldPrSet = createSet("old-pr", weight = 130.0, reps = 1, isPr = true, e1rm = 130.0)
        repo.seedSet(oldPrSet)

        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("new-set-1", weight = 100.0, reps = 5) // e1RM = ~116.7
        )

        val result = useCase(sets).getOrThrow()

        // Old PR set should not need update
        assertTrue(result.oldPrSetsToUpdate.isEmpty())

        // Incoming set is not PR
        val set1 = result.updatedIncomingSets.first { it.id.value == "new-set-1" }
        assertFalse(set1.isPersonalRecord)
    }

    @Test
    @DisplayName("Compute PRs ignores excluded set IDs when looking up existing PR")
    fun computePrs_ignoresExcludedPr() = runTest {
        val repo = FakeTrainingLogRepository()
        val oldPrSet = createSet("old-pr", weight = 130.0, reps = 1, isPr = true, e1rm = 130.0)
        repo.seedSet(oldPrSet)

        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("new-set-1", weight = 100.0, reps = 5) // e1RM = ~116.7
        )

        // Exclude the old-pr set ID (simulating replacement of imported history)
        val result = useCase(sets, excludeSetIds = setOf("old-pr")).getOrThrow()

        // It should treat it as if no PR exists and mark the new max set as PR
        val set1 = result.updatedIncomingSets.first { it.id.value == "new-set-1" }
        assertTrue(set1.isPersonalRecord)
    }

    @Test
    @DisplayName("Compute PRs ignores sets with reps <= 0 or null lift ID")
    fun computePrs_edgeCases() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("set-zero-reps", weight = 100.0, reps = 0),
            createSet("set-null-lift", weight = 100.0, reps = 5).copy(liftId = null)
        )

        val result = useCase(sets).getOrThrow()

        val setZero = result.updatedIncomingSets.first { it.id.value == "set-zero-reps" }
        val setNullLift = result.updatedIncomingSets.first { it.id.value == "set-null-lift" }

        assertNull(setZero.estimatedOneRm)
        assertFalse(setZero.isPersonalRecord)

        assertFalse(setNullLift.isPersonalRecord)
    }

    @Test
    @DisplayName("Compute PRs calculates e1RM considering reps + RIR")
    fun computePrs_considersRir() = runTest {
        val repo = FakeTrainingLogRepository()
        val useCase = ComputePersonalRecordsUseCase(repo)

        val sets = listOf(
            createSet("set-1", weight = 100.0, reps = 8, rir = 2) // effective reps = 8 + 2 = 10
        )

        val result = useCase(sets).getOrThrow()
        val set1 = result.updatedIncomingSets.first { it.id.value == "set-1" }

        // At 100 lb for 10 effective reps, e1RM should be exactly 137.04 (smooth spliced model)
        assertEquals(137.04, set1.estimatedOneRm!!, 0.05)
    }

    private fun createSet(
        id: String,
        weight: Double,
        reps: Int,
        isPr: Boolean = false,
        e1rm: Double? = null,
        rir: Int? = null
    ): LoggedSet {
        return LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Bench Press",
            liftId = liftId,
            setIndex = 1,
            weight = Weight(weight, Weight.WeightUnit.Lb),
            reps = reps,
            rir = rir,
            completedAt = Instant.now(),
            liftContext = LoggedLiftContext(
                liftName = "Bench Press"
            ),
            isPersonalRecord = isPr,
            estimatedOneRm = e1rm
        )
    }
}

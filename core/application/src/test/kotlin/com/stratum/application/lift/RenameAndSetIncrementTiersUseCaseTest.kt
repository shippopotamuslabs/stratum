package com.stratum.application.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersInvalidException
import com.stratum.domain.lift.Weight

class RenameAndSetIncrementTiersUseCaseTest {

    private val equipmentId = EquipmentId("pin-loaded-machine")

    private fun subject(repo: FakeEquipmentRepository): RenameAndSetIncrementTiersUseCase =
        RenameAndSetIncrementTiersUseCase(
            repo,
            FakeLoggingPort(),
        )

    private fun seed(): FakeEquipmentRepository =
        FakeEquipmentRepository(
            listOf(
                Equipment(
                    id = equipmentId,
                    name = "Pin-Loaded Machine",
                    incrementTiers = listOf(tier(0.0, null, 15.0)),
                ),
            ),
        )

    @Test
    @DisplayName("Given a valid name and tiers when invoked then both persist together")
    fun persistsNameAndTiers() = runTest {
        val repo = seed()
        val tiers = listOf(tier(0.0, null, 10.0))
        val micro = listOf(stop(5.0))

        val result = subject(repo)(equipmentId, "  Lat Stack  ", tiers, micro)

        assertTrue(result.isSuccess)
        val stored = repo.current.single()
        assertEquals("Lat Stack", stored.name)
        assertEquals(tiers, stored.incrementTiers)
        assertEquals(micro, stored.microIncrements)
    }

    @Test
    @DisplayName("Given a blank name when invoked then nothing is written")
    fun rejectsBlankName() = runTest {
        val repo = seed()
        val before = repo.current.single()

        val result = subject(repo)(equipmentId, "   ", listOf(tier(0.0, null, 10.0)))

        assertTrue(result.isFailure)
        assertEquals("Equipment name is required", result.exceptionOrNull()?.message)
        assertEquals(before, repo.current.single())
    }

    @Test
    @DisplayName("Given invalid tiers when invoked then the rename is not written either")
    fun rejectsInvalidTiersWithoutRenaming() = runTest {
        val repo = seed()

        val result =
            subject(repo)(
                equipmentId,
                "Lat Stack",
                listOf(tier(0.0, 40.0, 5.0), tier(50.0, null, 10.0)), // gap
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
        val stored = repo.current.single()
        assertEquals("Pin-Loaded Machine", stored.name)
        assertEquals(listOf(tier(0.0, null, 15.0)), stored.incrementTiers)
    }

    @Test
    @DisplayName("Given a repository failure when invoked then stored name and tiers are unchanged")
    fun repositoryFailureLeavesStateUnchanged() = runTest {
        val repo = seed()
        val before = repo.current.single()
        repo.failNextWrite = true

        val result = subject(repo)(equipmentId, "Lat Stack", listOf(tier(0.0, null, 10.0)))

        assertTrue(result.isFailure)
        assertEquals(before, repo.current.single())
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Lb)
}

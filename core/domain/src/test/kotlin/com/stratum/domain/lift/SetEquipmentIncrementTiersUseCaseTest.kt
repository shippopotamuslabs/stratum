package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SetEquipmentIncrementTiersUseCaseTest {

    private val equipmentId = EquipmentId("pin-loaded-machine")

    private fun subject(repo: FakeEquipmentRepository): SetEquipmentIncrementTiersUseCase =
        SetEquipmentIncrementTiersUseCase(
            repo,
            ValidateIncrementTiersUseCase(),
            ValidateMicroIncrementsUseCase(),
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
    @DisplayName("Given valid tiers when invoked then tiers persist")
    fun persistsValidTiers() = runTest {
        val repo = seed()
        val tiers = listOf(tier(0.0, null, 15.0))
        val micro = listOf(stop(5.0), stop(10.0))

        val result = subject(repo)(equipmentId, tiers, micro)

        assertTrue(result.isSuccess)
        assertEquals(tiers, repo.current.single().incrementTiers)
        assertEquals(micro, repo.current.single().microIncrements)
    }

    @Test
    @DisplayName("Given invalid tiers when invoked then repository is not written")
    fun rejectsInvalidTiers() = runTest {
        val repo = seed()
        val before = repo.current.single().incrementTiers

        val result =
            subject(repo)(
                equipmentId,
                listOf(tier(0.0, 40.0, 5.0), tier(50.0, null, 10.0)), // gap
            )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
        assertEquals(before, repo.current.single().incrementTiers)
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Lb)
}

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
import com.stratum.domain.lift.IncrementTiersValidationError
import com.stratum.domain.lift.Weight

class SetEquipmentIncrementTiersUseCaseTest {

    private val equipmentId = EquipmentId("pin-loaded-machine")

    private fun subject(repo: FakeEquipmentRepository): SetEquipmentIncrementTiersUseCase =
        SetEquipmentIncrementTiersUseCase(
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
    @DisplayName("Given valid tiers when invoked then tiers persist")
    fun persistsValidTiers() = runTest {
        val repo = seed()
        val tiers = listOf(tier(0.0, null, 15.0))
        val micro = listOf(stop(5.0), stop(10.0))

        val result = subject(repo)(equipmentId, tiers, micro)

        assertEquals(SetEquipmentIncrementTiersResult.Success, result)
        assertEquals(tiers, repo.current.single().incrementTiers)
        assertEquals(micro, repo.current.single().microIncrements)
    }

    @Test
    @DisplayName("Given invalid tiers when invoked then InvalidTiers and repository is not written")
    fun rejectsInvalidTiers() = runTest {
        val repo = seed()
        val before = repo.current.single().incrementTiers

        val result =
            subject(repo)(
                equipmentId,
                listOf(tier(0.0, 40.0, 5.0), tier(50.0, null, 10.0)), // gap
            )

        assertTrue(result is SetEquipmentIncrementTiersResult.InvalidTiers)
        assertTrue(
            (result as SetEquipmentIncrementTiersResult.InvalidTiers).validation.errors
                .contains(IncrementTiersValidationError.NotContiguous),
        )
        assertEquals(before, repo.current.single().incrementTiers)
    }

    @Test
    @DisplayName("Given a stop above the largest step when invoked then InvalidMicro (STR-243)")
    fun rejectsInvalidMicro() = runTest {
        val repo = seed()
        val before = repo.current.single().incrementTiers

        val result =
            subject(repo)(
                equipmentId,
                listOf(tier(0.0, null, 15.0)),
                listOf(stop(20.0)),
            )

        assertTrue(result is SetEquipmentIncrementTiersResult.InvalidMicro)
        assertEquals(before, repo.current.single().incrementTiers)
    }

    @Test
    @DisplayName("Given a repository failure when invoked then PersistenceFailed (STR-243)")
    fun persistenceFailure() = runTest {
        val repo = seed()
        repo.failNextWrite = true

        val result = subject(repo)(equipmentId, listOf(tier(0.0, null, 15.0)))

        assertEquals(SetEquipmentIncrementTiersResult.PersistenceFailed, result)
    }

    @Test
    @DisplayName("Cancellation propagates instead of becoming a branch (STR-243)")
    fun cancellationPropagates() {
        val cancelling =
            object : EquipmentRepository by seed() {
                override suspend fun setIncrementTiers(
                    id: EquipmentId,
                    tiers: List<IncrementTier>,
                    micro: List<Weight>?,
                ) {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
        val subject = SetEquipmentIncrementTiersUseCase(cancelling, FakeLoggingPort())
        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking {
                subject(equipmentId, listOf(tier(0.0, null, 15.0)))
            }
        }
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Weight.WeightUnit.Lb),
            upperBound = upper?.let { Weight(it, Weight.WeightUnit.Lb) },
            increment = Weight(inc, Weight.WeightUnit.Lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, Weight.WeightUnit.Lb)
}

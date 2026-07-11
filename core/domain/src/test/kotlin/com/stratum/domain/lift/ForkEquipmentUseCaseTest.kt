package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ForkEquipmentUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val customId = EquipmentId("custom-stack")

    private val multiTier =
        listOf(
            IncrementTier(Weight(0.0, lb), Weight(50.0, lb), Weight(5.0, lb)),
            IncrementTier(Weight(50.0, lb), null, Weight(10.0, lb)),
        )

    private lateinit var equipmentRepo: FakeEquipmentRepository
    private lateinit var subject: ForkEquipmentUseCase

    @BeforeEach
    fun setup() {
        equipmentRepo = FakeEquipmentRepository()
        equipmentRepo.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = multiTier,
                    isCustom = false,
                ),
                Equipment(
                    id = customId,
                    name = "My stack",
                    incrementTiers =
                        listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
                    isCustom = true,
                ),
            ),
        )
        subject =
            ForkEquipmentUseCase(
                equipmentRepository = equipmentRepo,
                validate = ValidateIncrementTiersUseCase(),
                loggingPort = FakeLoggingPort(),
            )
    }

    @Test
    @DisplayName("Given seed parent when forked then new custom has copied tiers and seed is untouched")
    fun forksSeedCopiesTiers() = runTest {
        val result = subject(cableId, "Life Fitness cables")

        assertTrue(result.isSuccess)
        val newId = result.getOrThrow()
        assertNotEquals(cableId, newId)
        val forked = equipmentRepo.getEquipment(newId)!!
        assertEquals("Life Fitness cables", forked.name)
        assertTrue(forked.isCustom)
        assertEquals(multiTier, forked.incrementTiers)
        assertEquals(multiTier, equipmentRepo.getEquipment(cableId)?.incrementTiers)
        assertEquals(false, equipmentRepo.getEquipment(cableId)?.isCustom)
    }

    @Test
    @DisplayName("Given custom parent when forked then tiers are copied without lineage")
    fun forksCustomParent() = runTest {
        val result = subject(customId, "My stack (copy)")

        assertTrue(result.isSuccess)
        val forked = equipmentRepo.getEquipment(result.getOrThrow())!!
        assertEquals(
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            forked.incrementTiers,
        )
        assertTrue(forked.isCustom)
        // Parent still present and unchanged.
        assertEquals("My stack", equipmentRepo.getEquipment(customId)?.name)
    }

    @Test
    @DisplayName("Given blank name when forked then failure and no write")
    fun rejectsBlankName() = runTest {
        val before = equipmentRepo.current.size
        val result = subject(cableId, "   ")
        assertTrue(result.isFailure)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName("Given missing parent when forked then failure")
    fun rejectsMissingParent() = runTest {
        val result = subject(EquipmentId("missing"), "Copy")
        assertTrue(result.isFailure)
    }

    @Test
    @DisplayName("Given empty-tier parent when forked then creates custom (repo default band)")
    fun forksEmptyTiers() = runTest {
        val emptyId = EquipmentId("empty-eq")
        equipmentRepo.seed(
            equipmentRepo.current +
                Equipment(
                    id = emptyId,
                    name = "Empty",
                    incrementTiers = emptyList(),
                    isCustom = true,
                ),
        )

        val result = subject(emptyId, "Empty copy")
        assertTrue(result.isSuccess)
        val forked = equipmentRepo.getEquipment(result.getOrThrow())!!
        // Fake/repo default single band when empty tiers passed.
        assertTrue(forked.incrementTiers.isNotEmpty())
    }
}

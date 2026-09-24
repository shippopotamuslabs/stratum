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
                validateMicro = ValidateMicroIncrementsUseCase(),
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
    @DisplayName("Given parent with stops when forked without micro then the copy inherits them")
    fun forkInheritsParentMicro() = runTest {
        val parentId = EquipmentId("pin-parent")
        val stops = listOf(Weight(2.5, lb), Weight(5.0, lb))
        equipmentRepo.seed(
            equipmentRepo.current +
                Equipment(
                    id = parentId,
                    name = "Pin parent",
                    incrementTiers = multiTier,
                    microIncrements = stops,
                    isCustom = true,
                ),
        )

        val result = subject(parentId, "Pin copy")

        assertTrue(result.isSuccess)
        assertEquals(stops, equipmentRepo.getEquipment(result.getOrThrow())?.microIncrements)
        assertEquals(stops, equipmentRepo.getEquipment(parentId)?.microIncrements)
    }

    @Test
    @DisplayName("Given parent with stops when forked with explicit micro then the copy carries it")
    fun forkReplacesMicroWithExplicit() = runTest {
        val parentId = EquipmentId("pin-parent")
        equipmentRepo.seed(
            equipmentRepo.current +
                Equipment(
                    id = parentId,
                    name = "Pin parent",
                    incrementTiers = multiTier,
                    microIncrements = listOf(Weight(2.5, lb), Weight(5.0, lb)),
                    isCustom = true,
                ),
        )

        val result = subject(parentId, "Pin copy", micro = listOf(Weight(2.5, lb)))

        assertTrue(result.isSuccess)
        val forked = equipmentRepo.getEquipment(result.getOrThrow())!!
        assertEquals(listOf(Weight(2.5, lb)), forked.microIncrements)
        assertEquals(
            listOf(Weight(2.5, lb), Weight(5.0, lb)),
            equipmentRepo.getEquipment(parentId)?.microIncrements,
        )
    }

    @Test
    @DisplayName("Given parent with stops when forked with empty micro then the copy has no knob")
    fun forkExplicitEmptyDropsKnob() = runTest {
        val parentId = EquipmentId("pin-parent")
        equipmentRepo.seed(
            equipmentRepo.current +
                Equipment(
                    id = parentId,
                    name = "Pin parent",
                    incrementTiers = multiTier,
                    microIncrements = listOf(Weight(2.5, lb)),
                    isCustom = true,
                ),
        )

        val result = subject(parentId, "Pin copy", micro = emptyList())

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Weight>(), equipmentRepo.getEquipment(result.getOrThrow())?.microIncrements)
        assertEquals(
            listOf(Weight(2.5, lb)),
            equipmentRepo.getEquipment(parentId)?.microIncrements,
        )
    }

    @Test
    @DisplayName("Given stop above the largest band when forked then failure and no write")
    fun forkRejectsInvalidMicro() = runTest {
        val before = equipmentRepo.current.size
        val result = subject(cableId, "Cable copy", micro = listOf(Weight(20.0, lb)))

        assertTrue(result.isFailure)
        assertEquals(before, equipmentRepo.current.size)
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

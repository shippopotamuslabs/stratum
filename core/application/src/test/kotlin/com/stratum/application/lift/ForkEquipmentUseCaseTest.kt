package com.stratum.application.lift

import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.domain.lift.identity.EquipmentId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight

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
            )
    }

    private fun ForkEquipmentResult.successForked(): ForkedEquipment {
        assertTrue(this is ForkEquipmentResult.Success)
        return (this as ForkEquipmentResult.Success).forked
    }

    @Test
    @DisplayName("Given seed parent when forked then new custom has copied tiers and seed is untouched")
    fun forksSeedCopiesTiers() = runTest {
        val result = subject(cableId, "Life Fitness cables")

        val newId = result.successForked().id
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

        val forked = equipmentRepo.getEquipment(result.successForked().id)!!
        assertEquals(
            listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb))),
            forked.incrementTiers,
        )
        assertTrue(forked.isCustom)
        // Parent still present and unchanged.
        assertEquals("My stack", equipmentRepo.getEquipment(customId)?.name)
    }

    @Test
    @DisplayName("Given the source's own name when forked then the copy takes the (1) suffix (STR-246)")
    fun forkWithCollidingNameGetsNumberedSuffix() = runTest {
        val result = subject(cableId, "Cables")

        val forked = equipmentRepo.getEquipment(result.successForked().id)!!
        assertEquals("Cables (1)", forked.name)
    }

    @Test
    @DisplayName("Given the base and (1) names taken when forked then the copy takes (2) (STR-246)")
    fun forkWithTwoCollisionsGetsNextNumber() = runTest {
        subject(cableId, "Cables")
        val result = subject(cableId, "Cables")

        val forked = equipmentRepo.getEquipment(result.successForked().id)!!
        assertEquals("Cables (2)", forked.name)
    }

    @Test
    @DisplayName("Given blank name when forked then InvalidName and no write")
    fun rejectsBlankName() = runTest {
        val before = equipmentRepo.current.size
        val result = subject(cableId, "   ")
        assertTrue(result is ForkEquipmentResult.InvalidName)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName("Given missing parent when forked then EquipmentNotFound")
    fun rejectsMissingParent() = runTest {
        val missingId = EquipmentId("missing")
        val result = subject(missingId, "Copy")
        assertEquals(ForkEquipmentResult.EquipmentNotFound(missingId), result)
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

        assertEquals(stops, equipmentRepo.getEquipment(result.successForked().id)?.microIncrements)
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

        val forked = equipmentRepo.getEquipment(result.successForked().id)!!
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

        assertEquals(emptyList<Weight>(), equipmentRepo.getEquipment(result.successForked().id)?.microIncrements)
        assertEquals(
            listOf(Weight(2.5, lb)),
            equipmentRepo.getEquipment(parentId)?.microIncrements,
        )
    }

    @Test
    @DisplayName("Given stop above the largest band when forked then InvalidMicro and no write")
    fun forkRejectsInvalidMicro() = runTest {
        val before = equipmentRepo.current.size
        val result = subject(cableId, "Cable copy", micro = listOf(Weight(20.0, lb)))

        assertTrue(result is ForkEquipmentResult.InvalidMicro)
        assertEquals(before, equipmentRepo.current.size)
    }

    @Test
    @DisplayName(
        "Given explicit micro above the parent's largest step when source validation " +
            "is skipped then the fork succeeds for destination-tier callers",
    )
    fun forkSkipsSourceValidationWhenAsked() = runTest {
        // Parent's largest step is 2.5; 5.0 targets the wider replacement bands.
        val result =
            subject(
                customId,
                "Stack copy",
                micro = listOf(Weight(5.0, lb)),
                validateMicroAgainstSource = false,
            )

        val forked = result.successForked()
        assertEquals(listOf(Weight(5.0, lb)), forked.microIncrements)
        assertEquals(
            listOf(Weight(5.0, lb)),
            equipmentRepo.getEquipment(forked.id)?.microIncrements,
        )
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
        val forked = equipmentRepo.getEquipment(result.successForked().id)!!
        // Fake/repo default single band when empty tiers passed.
        assertTrue(forked.incrementTiers.isNotEmpty())
    }

    @Test
    @DisplayName("Given a repository failure when forked then PersistenceFailed carrying the failure diagnostic")
    fun forkPersistenceFailure() = runTest {
        equipmentRepo.failNextWrite = true
        val result = subject(cableId, "Cable copy")

        val failed = result as ForkEquipmentResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("ForkEquipment", diagnostic.tag)
        assertEquals(
            "Failed to fork equipment: Simulated equipment write failure",
            diagnostic.message,
        )
        assertTrue(diagnostic.cause is IllegalStateException)
    }
}

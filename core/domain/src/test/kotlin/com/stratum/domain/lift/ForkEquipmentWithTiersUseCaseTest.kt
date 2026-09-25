package com.stratum.domain.lift

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ForkEquipmentWithTiersUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")

    private val storedTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val editedTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))

    private lateinit var equipmentRepo: FakeEquipmentRepository
    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var subject: ForkEquipmentWithTiersUseCase

    @BeforeEach
    fun setup() {
        equipmentRepo = FakeEquipmentRepository()
        equipmentRepo.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = storedTiers,
                    isCustom = false,
                ),
            ),
        )
        liftRepo = FakeLiftRepository()
        liftRepo.seedLifts(
            listOf(
                Lift(
                    id = LiftId("lat-pulldown"),
                    name = "Lat pulldown",
                    movementPatterns = setOf(MovementPattern.VerticalPull),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = emptySet(),
                    incrementTiers = emptyList(),
                    presetTags = emptySet(),
                    equipmentId = cableId,
                    usesEquipmentDefaultIncrements = true,
                    enabled = true,
                    loadType = LoadType.Weighted,
                ),
            ),
        )
        subject =
            ForkEquipmentWithTiersUseCase(
                forkEquipment =
                    ForkEquipmentUseCase(
                        equipmentRepo,
                        ValidateIncrementTiersUseCase(),
                        FakeLoggingPort(),
                    ),
                setEquipmentIncrementTiers =
                    SetEquipmentIncrementTiersUseCase(
                        equipmentRepo,
                        ValidateIncrementTiersUseCase(),
                        ValidateMicroIncrementsUseCase(),
                        FakeLoggingPort(),
                    ),
                setLiftEquipment = SetLiftEquipmentUseCase(liftRepo, FakeLoggingPort()),
            )
    }

    @Test
    @DisplayName(
        "Given edited tiers and a lift when invoked then the copy takes the edits, " +
            "the shared tiers are untouched, and the lift moves to the copy",
    )
    fun forksEditsAndReassigns() = runTest {
        val result =
            subject(cableId, "Cables (copy)", editedTiers, LiftId("lat-pulldown"))

        assertTrue(result.isSuccess)
        val copy = equipmentRepo.getEquipment(result.getOrThrow())!!
        assertEquals("Cables (copy)", copy.name)
        assertEquals(editedTiers, copy.incrementTiers)
        assertEquals(storedTiers, equipmentRepo.getEquipment(cableId)?.incrementTiers)
        assertEquals(
            copy.id,
            liftRepo.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single().equipmentId,
        )
    }

    @Test
    @DisplayName("Given no lift when invoked then the copy is made and lifts are untouched")
    fun forksWithoutReassign() = runTest {
        val result = subject(cableId, "Cables (copy)", editedTiers)

        assertTrue(result.isSuccess)
        assertEquals(
            editedTiers,
            equipmentRepo.getEquipment(result.getOrThrow())?.incrementTiers,
        )
        assertEquals(
            cableId,
            liftRepo.getLiftsByIds(listOf(LiftId("lat-pulldown"))).single().equipmentId,
        )
    }
}

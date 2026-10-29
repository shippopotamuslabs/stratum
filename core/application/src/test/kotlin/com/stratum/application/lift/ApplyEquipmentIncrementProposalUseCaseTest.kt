package com.stratum.application.lift

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersInvalidException
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight

class ApplyEquipmentIncrementProposalUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val customId = EquipmentId("custom-stack")
    private val latId = LiftId("lat-pulldown")
    private val rowId = LiftId("cable-row")

    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val proposed =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)))

    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var equipmentRepo: FakeEquipmentRepository
    private lateinit var logging: FakeLoggingPort
    private lateinit var subject: ApplyEquipmentIncrementProposalUseCase

    @BeforeEach
    fun setup() {
        liftRepo = FakeLiftRepository()
        equipmentRepo = FakeEquipmentRepository(liftRepository = liftRepo)
        equipmentRepo.seed(
            listOf(
                Equipment(
                    id = cableId,
                    name = "Cables",
                    incrementTiers = openFive,
                    isCustom = false,
                ),
                Equipment(
                    id = customId,
                    name = "My stack",
                    incrementTiers = openFive,
                    isCustom = true,
                ),
            ),
        )
        liftRepo.equipmentNamesById =
            mapOf(cableId to "Cables", customId to "My stack")
        liftRepo.seedLifts(
            listOf(
                sampleLift(latId, "Lat pulldown", cableId),
                sampleLift(rowId, "Cable row", cableId),
            ),
        )
        logging = FakeLoggingPort()
        subject =
            ApplyEquipmentIncrementProposalUseCase(
                equipmentRepository = equipmentRepo,
                loggingPort = logging,
            )
    }

    @Nested
    @DisplayName("AmendEquipmentTiers")
    inner class Amend {
        @Test
        @DisplayName("Given valid tiers when amend applied then equipment tiers update")
        fun amendsEquipmentTiers() = runTest {
            val remedy =
                EquipmentIncrementRemedy.AmendEquipmentTiers(
                    equipmentId = customId,
                    tiers = proposed,
                )

            val result = subject(remedy)

            assertTrue(result.isSuccess)
            assertEquals(
                ApplyEquipmentIncrementResult.Amended(customId),
                result.getOrNull(),
            )
            assertEquals(proposed, equipmentRepo.getEquipment(customId)?.incrementTiers)
        }

        @Test
        @DisplayName("Given invalid tiers when amend applied then failure and no write")
        fun rejectsInvalidTiers() = runTest {
            val invalid =
                listOf(
                    IncrementTier(Weight(0.0, lb), Weight(50.0, lb), Weight(5.0, lb)),
                    // gap at 50–100
                    IncrementTier(Weight(100.0, lb), null, Weight(10.0, lb)),
                )
            val before = equipmentRepo.getEquipment(customId)?.incrementTiers

            val result =
                subject(
                    EquipmentIncrementRemedy.AmendEquipmentTiers(customId, invalid),
                )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
            assertEquals(before, equipmentRepo.getEquipment(customId)?.incrementTiers)
        }

        @Test
        @DisplayName("Given missing equipment when amend applied then clear failure")
        fun missingEquipmentFailsClearly() = runTest {
            val missingId = EquipmentId("deleted-equipment")
            val result =
                subject(
                    EquipmentIncrementRemedy.AmendEquipmentTiers(
                        equipmentId = missingId,
                        tiers = proposed,
                    ),
                )

            assertTrue(result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
            assertTrue(
                error?.message?.contains("Equipment not found", ignoreCase = true) == true,
            )
            assertEquals(0, logging.errors.size)
            assertEquals(1, logging.warnings.size)
        }
    }

    @Nested
    @DisplayName("ForkCustomEquipment")
    inner class Fork {
        @Test
        @DisplayName("Given fork remedy when applied then custom created and signal lifts assigned")
        fun forksAndAssigns() = runTest {
            val remedy =
                EquipmentIncrementRemedy.ForkCustomEquipment(
                    suggestedName = "Lat pulldown stack",
                    tiers = proposed,
                    assignLiftIds = listOf(latId),
                )

            val result = subject(remedy)

            assertTrue(result.isSuccess)
            val forked = result.getOrNull() as ApplyEquipmentIncrementResult.Forked
            val created = equipmentRepo.getEquipment(forked.equipmentId)
            assertEquals("Lat pulldown stack", created?.name)
            assertEquals(true, created?.isCustom)
            assertEquals(proposed, created?.incrementTiers)
            assertEquals(forked.equipmentId, liftRepo.currentLifts.first { it.id == latId }.equipmentId)
            assertEquals(cableId, liftRepo.currentLifts.first { it.id == rowId }.equipmentId)
            assertEquals(listOf(latId), equipmentRepo.assignedLiftsByEquipment[forked.equipmentId])
        }

        @Test
        @DisplayName("Given blank fork name when applied then failure without non-fatal")
        fun blankNameFails() = runTest {
            val result =
                subject(
                    EquipmentIncrementRemedy.ForkCustomEquipment(
                        suggestedName = "   ",
                        tiers = proposed,
                        assignLiftIds = listOf(latId),
                    ),
                )
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, logging.errors.size)
            assertEquals(1, logging.warnings.size)
        }
    }

    private fun sampleLift(
        id: LiftId,
        name: String,
        equipmentId: EquipmentId,
    ): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openFive,
            presetTags = emptySet(),
            equipmentId = equipmentId,
            equipmentName = "Cables",
            enabled = true,
        )
}

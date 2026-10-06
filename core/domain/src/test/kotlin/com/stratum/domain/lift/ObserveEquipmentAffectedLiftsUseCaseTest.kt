package com.stratum.domain.lift

import app.cash.turbine.test
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

class ObserveEquipmentAffectedLiftsUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val dumbbellId = EquipmentId("dumbbell")
    private val openBand =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeLiftRepository = FakeLiftRepository()
    private lateinit var subject: ObserveEquipmentAffectedLiftsUseCase

    @BeforeEach
    fun setup() {
        subject = ObserveEquipmentAffectedLiftsUseCase(fakeLiftRepository)
    }

    @Nested
    @DisplayName("filterEquipmentAffectedLifts")
    inner class PureFilter {
        @Test
        @DisplayName("Given linked, unlinked and disabled lifts when filtered then all linked count")
        fun filtersLinkedLiftsIncludingDisabled() {
            val lifts =
                listOf(
                    sampleLift(
                        id = "lat-pulldown",
                        name = "Lat pulldown",
                        equipmentId = cableId,
                        enabled = true,
                    ),
                    sampleLift(
                        id = "cable-row",
                        name = "Cable row",
                        equipmentId = cableId,
                        enabled = false, // still counted
                    ),
                    sampleLift(
                        id = "db-press",
                        name = "DB press",
                        equipmentId = dumbbellId,
                        enabled = true,
                    ),
                    sampleLift(
                        id = "unassigned",
                        name = "Unassigned",
                        equipmentId = null,
                        enabled = true,
                    ),
                )

            val result = filterEquipmentAffectedLifts(cableId, lifts)

            assertEquals(2, result.count)
            assertEquals(
                listOf("Cable row", "Lat pulldown"),
                result.lifts.map { it.name },
            )
            assertTrue(result.lifts.any { !it.enabled })
        }

        @Test
        @DisplayName("Given no matching lifts when filtered then count is zero")
        fun emptyWhenNoneMatch() {
            val lifts =
                listOf(
                    sampleLift(
                        id = "db-press",
                        name = "DB press",
                        equipmentId = dumbbellId,
                    ),
                )
            val result = filterEquipmentAffectedLifts(cableId, lifts)
            assertEquals(0, result.count)
            assertTrue(result.lifts.isEmpty())
        }
    }

    @Nested
    @DisplayName("ObserveEquipmentAffectedLiftsUseCase")
    inner class UseCase {
        @Test
        @DisplayName("Given seeded catalog when observed then emits linked lifts for id")
        fun observesAffected() = runTest {
            fakeLiftRepository.seedLifts(
                listOf(
                    sampleLift(
                        id = "lat-pulldown",
                        name = "Lat pulldown",
                        equipmentId = cableId,
                    ),
                    sampleLift(
                        id = "face-pull",
                        name = "Face pull",
                        equipmentId = cableId,
                    ),
                    sampleLift(
                        id = "disabled-cable",
                        name = "Disabled cable fly",
                        equipmentId = cableId,
                        enabled = false,
                    ),
                ),
            )

            subject(cableId).test {
                val emitted = awaitItem()
                assertEquals(3, emitted.count)
                assertEquals(
                    listOf("Disabled cable fly", "Face pull", "Lat pulldown"),
                    emitted.lifts.map { it.name },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private fun sampleLift(
        id: String,
        name: String,
        equipmentId: EquipmentId?,
        enabled: Boolean = true,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openBand,
            presetTags = emptySet(),
            equipmentId = equipmentId,
            equipmentName = equipmentId?.value,
            enabled = enabled,
        )
}

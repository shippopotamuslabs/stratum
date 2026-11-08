package com.stratum.application.lift

import app.cash.turbine.test
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveEquipmentEditorStateUseCase] composition tests (STR-284): the
 * equipment-id + catalog + affected + prefs combine moved out of
 * `EquipmentIncrementsViewModel` must preserve the VM's prior resolution —
 * first catalog equipment matching the id, empty affected set for a blank id,
 * display unit from prefs.
 */
class ObserveEquipmentEditorStateUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeEquipmentRepository = FakeEquipmentRepository()
    private val fakeLiftRepository = FakeLiftRepository()
    private val fakePrefs = FakeWeightDisplayPreferencesRepository()

    private val subject =
        ObserveEquipmentEditorStateUseCase(
            ObserveEquipmentCatalogUseCase(fakeEquipmentRepository),
            ObserveEquipmentAffectedLiftsUseCase(fakeLiftRepository),
            ObserveWeightDisplayPreferencesUseCase(fakePrefs),
        )

    @Test
    @DisplayName("Given selected equipment when observed then snapshot carries equipment, affected, and prefs unit")
    fun selectedEquipmentResolved() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(
            listOf(
                lift("lat-pulldown", "Lat pulldown"),
                lift("cable-row", "Cable row"),
            ),
        )
        fakePrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))

        subject(MutableStateFlow("cable")).test {
            val snapshot = awaitItem()
            assertEquals("cable", snapshot.equipmentId)
            assertEquals("Cables", snapshot.equipment?.name)
            assertEquals(2, snapshot.affected.count)
            assertEquals(
                listOf("Cable row", "Lat pulldown"),
                snapshot.affected.lifts.map { it.name },
            )
            assertEquals(Weight.WeightUnit.Kg, snapshot.displayUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given unknown id when observed then snapshot carries null equipment and empty affected")
    fun unknownIdNullEquipment() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow("missing")).test {
            val snapshot = awaitItem()
            assertEquals("missing", snapshot.equipmentId)
            assertNull(snapshot.equipment)
            assertEquals(0, snapshot.affected.count)
            assertTrue(snapshot.affected.lifts.isEmpty())
            assertEquals(Weight.WeightUnit.Lb, snapshot.displayUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given blank id when observed then snapshot carries null equipment and empty affected")
    fun blankIdEmptyAffected() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow<String?>(null)).test {
            val snapshot = awaitItem()
            assertNull(snapshot.equipment)
            assertEquals(0, snapshot.affected.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given catalog change when observed then snapshot re-resolves the equipment")
    fun catalogChangeReResolves() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))
        val equipmentId = MutableStateFlow<String?>("cable")

        subject(equipmentId).test {
            assertEquals("Cables", awaitItem().equipment?.name)
            fakeEquipmentRepository.seed(
                listOf(
                    Equipment(id = cableId, name = "Cables v2", incrementTiers = openFive),
                ),
            )
            assertEquals("Cables v2", awaitItem().equipment?.name)
            equipmentId.value = null
            val cleared = awaitItem()
            assertNull(cleared.equipment)
            assertEquals(0, cleared.affected.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given prefs change when observed then snapshot carries the new unit")
    fun prefsChangeUpdatesUnit() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow("cable")).test {
            assertEquals(Weight.WeightUnit.Lb, awaitItem().displayUnit)
            fakePrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))
            assertEquals(Weight.WeightUnit.Kg, awaitItem().displayUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given lift change when observed then snapshot carries the updated affected set")
    fun liftChangeUpdatesAffected() = runTest {
        fakeEquipmentRepository.seed(
            listOf(
                Equipment(id = cableId, name = "Cables", incrementTiers = openFive),
            ),
        )
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow("cable")).test {
            assertEquals(1, awaitItem().affected.count)
            fakeLiftRepository.seedLifts(
                listOf(
                    lift("lat-pulldown", "Lat pulldown"),
                    lift("cable-row", "Cable row"),
                ),
            )
            assertEquals(2, awaitItem().affected.count)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun lift(id: String, name: String): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(MovementPattern.VerticalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = emptySet(),
            incrementTiers = openFive,
            presetTags = emptySet(),
            equipmentId = cableId,
            equipmentName = "Cables",
            enabled = true,
        )
}

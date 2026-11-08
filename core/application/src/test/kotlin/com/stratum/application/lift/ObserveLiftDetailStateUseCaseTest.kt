package com.stratum.application.lift

import app.cash.turbine.test
import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveLiftDetailStateUseCase] composition tests (STR-285): the lift-id +
 * catalog + prefs combine moved out of `LiftInformationViewModel` must
 * preserve the VM's prior resolution — first catalog lift matching the id,
 * display unit from prefs.
 */
class ObserveLiftDetailStateUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val cableId = EquipmentId("cable")
    private val openFive =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

    private val fakeLiftRepository = FakeLiftRepository()
    private val fakePrefs = FakeWeightDisplayPreferencesRepository()

    private val subject =
        ObserveLiftDetailStateUseCase(
            ObserveLiftCatalogUseCase(fakeLiftRepository),
            ObserveWeightDisplayPreferencesUseCase(fakePrefs),
        )

    @Test
    @DisplayName("Given selected lift when observed then snapshot carries lift and prefs unit")
    fun selectedLiftResolved() = runTest {
        fakeLiftRepository.seedLifts(
            listOf(
                lift("lat-pulldown", "Lat pulldown"),
                lift("cable-row", "Cable row"),
            ),
        )
        fakePrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))

        subject(MutableStateFlow("cable-row")).test {
            val snapshot = awaitItem()
            assertEquals("cable-row", snapshot.liftId)
            assertEquals("Cable row", snapshot.lift?.name)
            assertEquals(Weight.WeightUnit.Kg, snapshot.displayUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given unknown id when observed then snapshot carries null lift")
    fun unknownIdNullLift() = runTest {
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow("missing")).test {
            val snapshot = awaitItem()
            assertEquals("missing", snapshot.liftId)
            assertNull(snapshot.lift)
            assertEquals(Weight.WeightUnit.Lb, snapshot.displayUnit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given catalog change when observed then snapshot re-resolves the lift")
    fun catalogChangeReResolves() = runTest {
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))
        val liftId = MutableStateFlow<String?>("lat-pulldown")

        subject(liftId).test {
            assertEquals("Lat pulldown", awaitItem().lift?.name)
            fakeLiftRepository.seedLifts(
                listOf(
                    lift("lat-pulldown", "Lat pulldown v2"),
                    lift("cable-row", "Cable row"),
                ),
            )
            assertEquals("Lat pulldown v2", awaitItem().lift?.name)
            liftId.value = "cable-row"
            val reselected = awaitItem()
            assertEquals("cable-row", reselected.liftId)
            assertEquals("Cable row", reselected.lift?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given prefs change when observed then snapshot carries the new unit")
    fun prefsChangeUpdatesUnit() = runTest {
        fakeLiftRepository.seedLifts(listOf(lift("lat-pulldown", "Lat pulldown")))

        subject(MutableStateFlow("lat-pulldown")).test {
            assertEquals(Weight.WeightUnit.Lb, awaitItem().displayUnit)
            fakePrefs.seed(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg))
            assertEquals(Weight.WeightUnit.Kg, awaitItem().displayUnit)
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

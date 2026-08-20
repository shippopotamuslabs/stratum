package com.stratum.core.ui.component.platecalculator

import com.stratum.domain.lift.PlateCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PlateLoadUi mapping")
class PlateLoadUiTest {

    @Test
    @DisplayName("Given trailing decimal when parsePlateTargetWeight then keeps integer prefix")
    fun trailingDecimalKeepsPrefix() {
        assertEquals(135.0, parsePlateTargetWeight("135.", fallback = 45.0))
    }

    @Test
    @DisplayName("Given blank text when parsePlateTargetWeight then uses positive fallback")
    fun blankUsesFallback() {
        assertEquals(185.0, parsePlateTargetWeight("", fallback = 185.0))
        assertEquals(0.0, parsePlateTargetWeight(".", fallback = 0.0))
    }

    @Test
    @DisplayName("Given unmatched 153 lb when computePlateLoadUi then reports remainder")
    fun unmatchedWeightSurfacesRemainder() {
        val load = computePlateLoadUi(
            targetWeight = 153.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
            unitLabel = "lb",
        )
        assertFalse(load.isValid)
        assertEquals(PlateLoadStatusUi.CannotMatchWithAvailablePlates, load.status)
        assertTrue(load.remainder > 0.0)
    }

    @Test
    @DisplayName("Given target below bar when computePlateLoadUi then reports BelowBarWeight")
    fun belowBarWeightStatus() {
        val load = computePlateLoadUi(
            targetWeight = 30.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
            unitLabel = "lb",
        )
        assertEquals(PlateLoadStatusUi.BelowBarWeight, load.status)
        assertFalse(load.isValid)
    }

    @Test
    @DisplayName("Given single-sided 95 lb when computePlateLoadUi then does not double plates")
    fun singleSidedDoesNotDouble() {
        val load = computePlateLoadUi(
            targetWeight = 95.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
            unitLabel = "lb",
            isDoubleSided = false,
        )
        assertTrue(load.isValid)
        assertEquals(95.0, load.totalWeight)
        assertEquals(50.0, load.totalPlateWeight)
    }

    @Test
    @DisplayName("Given defaultPlateInventory when lb then uses olympic bar and default plates")
    fun defaultInventoryIsLbOlympic() {
        val inventory = defaultPlateInventory("lb")
        assertEquals(PlateCalculator.OLYMPIC_BAR_LB, inventory.barWeight)
        assertEquals(PlateCalculator.DEFAULT_PLATES_LB.toSet(), inventory.enabledPlates)
        assertEquals("lb", inventory.unitLabel)
    }

    @Test
    @DisplayName("Given 225 lb with 45s disabled when computePlateLoadUi then uses 35s and 10s")
    fun disabled45sFor225Uses35sAnd10s() {
        val load = computePlateLoadUi(
            targetWeight = 225.0,
            barWeight = 45.0,
            availablePlates = listOf(35.0, 25.0, 10.0, 5.0, 2.5),
            unitLabel = "lb",
        )
        assertTrue(load.isValid)
        assertEquals(listOf(35.0, 10.0), load.platesPerSide.map { it.weight })
        assertEquals(listOf(2, 2), load.platesPerSide.map { it.count })
    }
}

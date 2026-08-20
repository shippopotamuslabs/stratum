package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PlateCalculatorTest {

    @Test
    @DisplayName("Given 0 target weight when calculatePlates then returns empty plates and valid status")
    fun zeroTargetWeightReturnsEmptyValid() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 0.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(PlateCalculationIssue.None, result.issue)
        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(0.0, result.totalWeight)
    }

    @Test
    @DisplayName("Given target weight equal to bar weight when calculatePlates then returns empty plates and valid status")
    fun targetEqualToBarWeightReturnsEmptyValid() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 45.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(PlateCalculationIssue.None, result.issue)
        assertTrue(result.platesPerSide.isEmpty())
        assertEquals(45.0, result.totalWeight)
    }

    @Test
    @DisplayName("Given standard 225 lb on 45 lb bar when calculatePlates then returns two 45 lb plates per side")
    fun standard225LbsCalculatesCorrectly() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 225.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(PlateCalculationIssue.None, result.issue)
        assertEquals(1, result.platesPerSide.size)
        assertEquals(PlateCount(45.0, 2), result.platesPerSide[0])
        assertEquals(225.0, result.totalWeight)
        assertEquals(90.0, result.totalPlateWeightPerSide)
        assertEquals(180.0, result.totalPlateWeight)
    }

    @Test
    @DisplayName("Given 135 lb on 45 lb bar when calculatePlates then returns one 45 lb plate per side")
    fun standard135LbsCalculatesCorrectly() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 135.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(PlateCalculationIssue.None, result.issue)
        assertEquals(1, result.platesPerSide.size)
        assertEquals(PlateCount(45.0, 1), result.platesPerSide[0])
        assertEquals(135.0, result.totalWeight)
    }

    @Test
    @DisplayName("Given 185 lb on 45 lb bar when calculatePlates then returns 45 + 25 per side")
    fun standard185LbsCalculatesCorrectly() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 185.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(2, result.platesPerSide.size)
        assertEquals(PlateCount(45.0, 1), result.platesPerSide[0])
        assertEquals(PlateCount(25.0, 1), result.platesPerSide[1])
        assertEquals(185.0, result.totalWeight)
    }

    @Test
    @DisplayName("Given fractional weight 142.5 lb on 45 lb bar when calculatePlates then returns 45 + 2.5 + 1.25 per side")
    fun fractional142Point5LbsCalculatesCorrectly() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 142.5,
            barWeight = 45.0,
            availablePlates = PlateCalculator.ALL_PLATES_LB,
        )

        assertTrue(result.isValid)
        assertEquals(3, result.platesPerSide.size)
        assertEquals(PlateCount(45.0, 1), result.platesPerSide[0])
        assertEquals(PlateCount(2.5, 1), result.platesPerSide[1])
        assertEquals(PlateCount(1.25, 1), result.platesPerSide[2])
        assertEquals(142.5, result.totalWeight)
    }

    @Test
    @DisplayName("Given target weight below bar weight when calculatePlates then returns BelowBarWeight issue")
    fun targetBelowBarWeightReturnsBelowBarWeightIssue() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 30.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertFalse(result.isValid)
        assertEquals(PlateCalculationIssue.BelowBarWeight, result.issue)
    }

    @Test
    @DisplayName("Given target weight with impossible plate combinations when calculatePlates then returns CannotMatchWithAvailablePlates")
    fun impossibleWeightReturnsCannotMatchIssue() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 153.0, // Cannot make 54 lb per side with standard plates (smallest is 1.25)
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
        )

        assertFalse(result.isValid)
        assertEquals(PlateCalculationIssue.CannotMatchWithAvailablePlates, result.issue)
    }

    @Test
    @DisplayName("Given 45 lb plates disabled when calculating 225 lb then decomposes using 35s and 25s")
    fun disabledPlatesDecomposesWithRemainingPlates() {
        val availableWithout45s = listOf(35.0, 25.0, 10.0, 5.0, 2.5)
        val result = PlateCalculator.calculatePlates(
            targetWeight = 225.0,
            barWeight = 45.0,
            availablePlates = availableWithout45s,
        )

        assertTrue(result.isValid)
        assertEquals(225.0, result.totalWeight)
        // 90 per side = 2x35 + 2x10 = 70+20=90
        assertEquals(90.0, result.totalPlateWeightPerSide)
    }

    @Test
    @DisplayName("Given single sided loading when calculatePlates then calculates for 1 side multiplier")
    fun singleSidedLoadingCalculatesCorrectly() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 95.0,
            barWeight = 45.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_LB,
            isDoubleSided = false,
        )

        assertTrue(result.isValid)
        // 95 - 45 = 50 on the single sleeve = 45 + 5
        assertEquals(2, result.platesPerSide.size)
        assertEquals(PlateCount(45.0, 1), result.platesPerSide[0])
        assertEquals(PlateCount(5.0, 1), result.platesPerSide[1])
        assertEquals(95.0, result.totalWeight)
    }

    @Test
    @DisplayName("Given KG unit when calculatePlates then operates on KG plate denominations")
    fun kgCalculationsWorkAccurately() {
        val result = PlateCalculator.calculatePlates(
            targetWeight = 100.0,
            barWeight = 20.0,
            availablePlates = PlateCalculator.DEFAULT_PLATES_KG,
        )

        assertTrue(result.isValid)
        // (100 - 20) / 2 = 40 kg per side = 1x25 + 1x15
        assertEquals(2, result.platesPerSide.size)
        assertEquals(PlateCount(25.0, 1), result.platesPerSide[0])
        assertEquals(PlateCount(15.0, 1), result.platesPerSide[1])
        assertEquals(100.0, result.totalWeight)
    }
}

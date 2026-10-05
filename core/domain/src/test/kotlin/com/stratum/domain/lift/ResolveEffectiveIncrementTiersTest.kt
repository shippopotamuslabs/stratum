package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveEffectiveIncrementTiersTest {

    private val lb = Weight.WeightUnit.Lb
    private val equipmentTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val fallbackTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))
    private val adderStops = listOf(Weight(5.0, lb), Weight(10.0, lb))

    @Test
    @DisplayName("Given equipment tiers when resolved then equipment defaults apply")
    fun equipmentDefaults() {
        val result =
            resolveEffectiveIncrementTiers(
                equipmentTiers = equipmentTiers,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(equipmentTiers, result.tiers)
        assertTrue(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given no equipment when resolved then fallback applies")
    fun fallbackApplies() {
        val result =
            resolveEffectiveIncrementTiers(
                equipmentTiers = null,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(fallbackTiers, result.tiers)
        assertFalse(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given empty equipment tiers when resolved then fallback applies")
    fun emptyEquipmentFallsBack() {
        val result =
            resolveEffectiveIncrementTiers(
                equipmentTiers = emptyList(),
                fallbackTiers = fallbackTiers,
            )
        assertEquals(fallbackTiers, result.tiers)
        assertFalse(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given equipment micro when resolved then micro is inherited")
    fun equipmentMicroInherited() {
        val result =
            resolveEffectiveIncrementTiers(
                equipmentTiers = equipmentTiers,
                fallbackTiers = fallbackTiers,
                equipmentMicro = adderStops,
            )
        assertEquals(adderStops, result.microIncrements)
    }

    @Test
    @DisplayName("Given no micro anywhere when resolved then micro is empty")
    fun noMicroAnywhere() {
        val result =
            resolveEffectiveIncrementTiers(
                equipmentTiers = equipmentTiers,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(emptyList<Weight>(), result.microIncrements)
    }
}

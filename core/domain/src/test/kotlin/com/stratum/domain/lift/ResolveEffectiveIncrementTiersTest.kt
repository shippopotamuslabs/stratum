package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveEffectiveIncrementTiersTest {

    private val lb = Weight.WeightUnit.Lb
    private val overrideTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(2.5, lb)))
    private val equipmentTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val fallbackTiers =
        listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))

    @Test
    @DisplayName("Given lift override tiers when resolved then override wins")
    fun overrideWins() {
        val result =
            resolveEffectiveIncrementTiers(
                liftOverrideTiers = overrideTiers,
                equipmentTiers = equipmentTiers,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(overrideTiers, result.tiers)
        assertFalse(result.usesEquipmentDefaultIncrements)
        assertFalse(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given empty lift overrides when resolved then equipment defaults apply")
    fun equipmentDefaults() {
        val result =
            resolveEffectiveIncrementTiers(
                liftOverrideTiers = emptyList(),
                equipmentTiers = equipmentTiers,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(equipmentTiers, result.tiers)
        assertTrue(result.usesEquipmentDefaultIncrements)
        assertTrue(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given no override and no equipment when resolved then fallback applies")
    fun fallbackApplies() {
        val result =
            resolveEffectiveIncrementTiers(
                liftOverrideTiers = emptyList(),
                equipmentTiers = null,
                fallbackTiers = fallbackTiers,
            )
        assertEquals(fallbackTiers, result.tiers)
        assertTrue(result.usesEquipmentDefaultIncrements)
        assertFalse(result.inheritsFromEquipmentLattice)
    }

    @Test
    @DisplayName("Given empty equipment tiers when resolved then fallback applies")
    fun emptyEquipmentFallsBack() {
        val result =
            resolveEffectiveIncrementTiers(
                liftOverrideTiers = emptyList(),
                equipmentTiers = emptyList(),
                fallbackTiers = fallbackTiers,
            )
        assertEquals(fallbackTiers, result.tiers)
        assertTrue(result.usesEquipmentDefaultIncrements)
        assertFalse(result.inheritsFromEquipmentLattice)
    }
}

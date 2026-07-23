package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
// toStorageLb is an extension in WeightConversion.kt

@DisplayName("WeightConversion")
class WeightConversionTest {

    @Test
    @DisplayName("same unit is identity")
    fun sameUnitIdentity() {
        assertEquals(135.0, WeightConversion.convertAmount(135.0, Weight.WeightUnit.Lb, Weight.WeightUnit.Lb))
        assertEquals(100.0, WeightConversion.convertAmount(100.0, Weight.WeightUnit.Kg, Weight.WeightUnit.Kg))
    }

    @Test
    @DisplayName("lb to kg uses fixed factor")
    fun lbToKg() {
        val kg = WeightConversion.convertAmount(100.0, Weight.WeightUnit.Lb, Weight.WeightUnit.Kg)
        assertEquals(45.359237, kg, 1e-9)
    }

    @Test
    @DisplayName("kg to lb is inverse of lb to kg")
    fun kgToLbRoundTrip() {
        val original = 225.0
        val kg = WeightConversion.convertAmount(original, Weight.WeightUnit.Lb, Weight.WeightUnit.Kg)
        val back = WeightConversion.convertAmount(kg, Weight.WeightUnit.Kg, Weight.WeightUnit.Lb)
        assertEquals(original, back, 1e-9)
    }

    @Test
    @DisplayName("Weight.convertTo preserves unit and amount when already target")
    fun convertToSameInstanceSemantics() {
        val w = Weight(135.0, Weight.WeightUnit.Lb)
        val same = w.convertTo(Weight.WeightUnit.Lb)
        assertEquals(w, same)
    }

    @Test
    @DisplayName("Weight.convertTo lb → kg")
    fun convertToKg() {
        val converted = Weight(100.0, Weight.WeightUnit.Lb).convertTo(Weight.WeightUnit.Kg)
        assertEquals(Weight.WeightUnit.Kg, converted.unit)
        assertEquals(45.359237, converted.amount, 1e-9)
    }

    @Test
    @DisplayName("shortLabel is lb / kg")
    fun shortLabel() {
        assertEquals("lb", Weight.WeightUnit.Lb.shortLabel())
        assertEquals("kg", Weight.WeightUnit.Kg.shortLabel())
    }

    @Test
    @DisplayName("default display preferences are lb")
    fun defaultPreferences() {
        assertEquals(Weight.WeightUnit.Lb, WeightDisplayPreferences().displayUnit)
    }

    @Test
    @DisplayName("toStorageLb and amountInLb")
    fun toStorageLb() {
        val kg = Weight(100.0, Weight.WeightUnit.Kg)
        val lb = kg.toStorageLb()
        assertEquals(Weight.WeightUnit.Lb, lb.unit)
        assertEquals(WeightConversion.amountInLb(kg), lb.amount, 1e-9)
    }

    @Test
    @DisplayName("loadMagnitudeLb abs for assisted negatives")
    fun loadMagnitudeLbAssisted() {
        val assisted = Weight(-50.0, Weight.WeightUnit.Lb)
        assertEquals(50.0, WeightConversion.loadMagnitudeLb(assisted), 1e-9)
        val assistedKg = Weight(-10.0, Weight.WeightUnit.Kg)
        assertEquals(
            WeightConversion.amountInLb(Weight(10.0, Weight.WeightUnit.Kg)),
            WeightConversion.loadMagnitudeLb(assistedKg),
            1e-9,
        )
    }

    @Test
    @DisplayName("storageWeightFromDisplay kg weighted → positive Lb")
    fun storageFromDisplayKgWeighted() {
        val stored =
            storageWeightFromDisplay(
                displayAmount = 100.0,
                displayUnit = Weight.WeightUnit.Kg,
                loadType = LoadType.Weighted,
            )
        assertEquals(Weight.WeightUnit.Lb, stored.unit)
        assertEquals(WeightConversion.KG_TO_LB * 100.0, stored.amount, 1e-9)
    }

    @Test
    @DisplayName("storageWeightFromDisplay assisted → negative Lb")
    fun storageFromDisplayAssisted() {
        val stored =
            storageWeightFromDisplay(
                displayAmount = 50.0,
                displayUnit = Weight.WeightUnit.Lb,
                loadType = LoadType.Assisted,
            )
        assertEquals(Weight(-50.0, Weight.WeightUnit.Lb), stored)
    }

    @Test
    @DisplayName("storageWeightFromDisplay bodyweight → 0 Lb")
    fun storageFromDisplayBodyweight() {
        val stored =
            storageWeightFromDisplay(
                displayAmount = 135.0,
                displayUnit = Weight.WeightUnit.Kg,
                loadType = LoadType.Bodyweight,
            )
        assertEquals(Weight(0.0, Weight.WeightUnit.Lb), stored)
    }

    @Test
    @DisplayName("nearlyEqualLb tolerates conversion epsilon")
    fun nearlyEqualLb() {
        val a = WeightConversion.amountInLb(Weight(100.0, Weight.WeightUnit.Kg))
        val b = a + WeightConversion.LOAD_AMOUNT_EPS / 2
        assertEquals(true, WeightConversion.nearlyEqualLb(a, b))
        assertEquals(false, WeightConversion.nearlyEqualLb(a, a + 1.0))
    }
}

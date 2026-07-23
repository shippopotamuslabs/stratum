package com.stratum.core.data.weightdisplay

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightDisplayPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WeightDisplayPreferencesMapper")
class WeightDisplayPreferencesMapperTest {

    @Test
    @DisplayName("unknown stored unit maps to Lb")
    fun unknownMapsToLb() {
        val domain = WeightDisplayPreferencesRecord(displayUnit = "NotAUnit").toDomain()
        assertEquals(WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Lb), domain)
    }

    @Test
    @DisplayName("Kg round-trips")
    fun kgRoundTrip() {
        val original = WeightDisplayPreferences(displayUnit = Weight.WeightUnit.Kg)
        assertEquals(original, original.toRecord().toDomain())
    }

    @Test
    @DisplayName("default record is Lb")
    fun defaultIsLb() {
        assertEquals(WeightDisplayPreferences(), WeightDisplayPreferencesRecord().toDomain())
    }
}

package com.stratum.core.ui.component

import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("formatRepsWithPartials / formatSetPerformance / formatWeightForDisplay")
class WorkoutDisplayFormattingTest {

    @Test
    @DisplayName("full reps only")
    fun fullRepsOnly() {
        assertEquals("5", formatRepsWithPartials(5, null))
        assertEquals("5", formatRepsWithPartials(5, 0))
    }

    @Test
    @DisplayName("full plus partials")
    fun fullPlusPartials() {
        assertEquals("5+2", formatRepsWithPartials(5, 2))
    }

    @Test
    @DisplayName("weighted set includes partials")
    fun weightedWithPartials() {
        assertEquals("185 × 5+2", formatSetPerformance(185.0, 5, 2))
    }

    @Test
    @DisplayName("bodyweight omits zero load and keeps +N")
    fun bodyweightWithPartials() {
        assertEquals("12+1", formatSetPerformance(0.0, 12, 1))
    }

    @Test
    @DisplayName("unitless zero renders as 0, not blank")
    fun unitlessZeroRendersZero() {
        val zero = Weight(0.0, Weight.WeightUnit.Lb)
        assertEquals("0", formatWeightForDisplay(zero, Weight.WeightUnit.Lb, includeUnit = false))
        assertEquals("0 lb", formatWeightForDisplay(zero, Weight.WeightUnit.Lb, includeUnit = true))
    }

    @Test
    @DisplayName("unitless decimal weight drops the unit")
    fun unitlessDecimalWeight() {
        val weight = Weight(42.5, Weight.WeightUnit.Lb)
        assertEquals("42.5", formatWeightForDisplay(weight, Weight.WeightUnit.Lb, includeUnit = false))
        assertEquals("42.5 lb", formatWeightForDisplay(weight, Weight.WeightUnit.Lb, includeUnit = true))
    }
}

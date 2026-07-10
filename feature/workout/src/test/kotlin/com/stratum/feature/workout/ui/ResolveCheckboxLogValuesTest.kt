package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveCheckboxLogValuesTest {

    @Test
    fun `empty fields use recommended weight and recommended reps`() {
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "",
            enteredReps = "",
            showWeightInput = true,
            recommendedWeightAmount = 135.0,
            recommendedReps = 8,
            repMin = 4,
        )
        assertEquals(135.0, weight)
        assertEquals(8, reps)
    }

    @Test
    fun `empty reps prefer recommendedReps over repMin zero backoff`() {
        // RIR-only backoff slots often have repMin=0 while still carrying a concrete
        // projected rep suggestion — that suggestion must be what gets logged.
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "",
            enteredReps = "",
            showWeightInput = true,
            recommendedWeightAmount = 120.0,
            recommendedReps = 10,
            repMin = 0,
        )
        assertEquals(120.0, weight)
        assertEquals(10, reps)
    }

    @Test
    fun `empty reps with no concrete recommendation fall back to repMin`() {
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "",
            enteredReps = "",
            showWeightInput = true,
            recommendedWeightAmount = 100.0,
            recommendedReps = 0,
            repMin = 4,
        )
        assertEquals(100.0, weight)
        assertEquals(4, reps)
    }

    @Test
    fun `typed values override suggestions`() {
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "140",
            enteredReps = "6",
            showWeightInput = true,
            recommendedWeightAmount = 135.0,
            recommendedReps = 8,
            repMin = 4,
        )
        assertEquals(140.0, weight)
        assertEquals(6, reps)
    }

    @Test
    fun `bodyweight forces zero weight and still resolves reps from suggestion`() {
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "999",
            enteredReps = "",
            showWeightInput = false,
            recommendedWeightAmount = 0.0,
            recommendedReps = 12,
            repMin = 8,
        )
        assertEquals(0.0, weight)
        assertEquals(12, reps)
    }

    @Test
    fun `invalid typed weight and reps become zero`() {
        val (weight, reps) = resolveCheckboxLogValues(
            enteredWeight = "abc",
            enteredReps = "xyz",
            showWeightInput = true,
            recommendedWeightAmount = 135.0,
            recommendedReps = 8,
            repMin = 4,
        )
        assertEquals(0.0, weight)
        assertEquals(0, reps)
    }
}

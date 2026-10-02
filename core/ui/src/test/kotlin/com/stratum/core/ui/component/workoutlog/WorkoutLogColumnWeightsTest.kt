package com.stratum.core.ui.component.workoutlog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutLogColumnWeightsTest {

    @Test
    @DisplayName("weighted with trailing narrows reps to buffer badge from checkbox")
    fun weightedWithTrailing() {
        val weights = workoutLogColumnWeights(showWeightColumn = true, showTrailingColumn = true)
        assertEquals(0.10f, weights.label, DELTA)
        assertEquals(0.26f, weights.prev, DELTA)
        assertEquals(0.26f, weights.weight, DELTA)
        assertEquals(0.22f, weights.reps, DELTA)
        assertEquals(0.16f, weights.trailing, DELTA)
        assertSumToOne(weights)
    }

    @Test
    @DisplayName("weighted without trailing splits the removed RIR fraction across prev, weight, reps")
    fun weightedWithoutTrailing() {
        val weights = workoutLogColumnWeights(showWeightColumn = true, showTrailingColumn = false)
        assertEquals(0.12f, weights.label, DELTA)
        assertEquals(0.30f, weights.prev, DELTA)
        assertEquals(0.29f, weights.weight, DELTA)
        assertEquals(0.29f, weights.reps, DELTA)
        assertEquals(0f, weights.trailing, DELTA)
        assertSumToOne(weights)
    }

    @Test
    @DisplayName("bodyweight with trailing narrows reps to buffer badge from checkbox")
    fun bodyweightWithTrailing() {
        val weights = workoutLogColumnWeights(showWeightColumn = false, showTrailingColumn = true)
        assertEquals(0.12f, weights.label, DELTA)
        assertEquals(0.39f, weights.prev, DELTA)
        assertEquals(0f, weights.weight, DELTA)
        assertEquals(0.33f, weights.reps, DELTA)
        assertEquals(0.16f, weights.trailing, DELTA)
        assertSumToOne(weights)
    }

    @Test
    @DisplayName("bodyweight without trailing splits the removed RIR fraction across prev and reps")
    fun bodyweightWithoutTrailing() {
        val weights = workoutLogColumnWeights(showWeightColumn = false, showTrailingColumn = false)
        assertEquals(0.14f, weights.label, DELTA)
        assertEquals(0.44f, weights.prev, DELTA)
        assertEquals(0f, weights.weight, DELTA)
        assertEquals(0.42f, weights.reps, DELTA)
        assertEquals(0f, weights.trailing, DELTA)
        assertSumToOne(weights)
    }

    private fun assertSumToOne(weights: WorkoutLogColumnWeights) {
        val sum = weights.label + weights.prev + weights.weight + weights.reps + weights.trailing
        assertEquals(1f, sum, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}

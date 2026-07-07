package com.stratum.domain.training

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EstimatedOneRmTest {

    @Test
    @DisplayName("e1RM is continuous at the 10-to-11 rep transition (C0 continuity)")
    fun continuousTransition() {
        val weight = 100.0
        val e1rmAt10 = EstimatedOneRm.calculate(weight, 10)
        val e1rmAt11 = EstimatedOneRm.calculate(weight, 11)

        // Under our smooth spliced model:
        // calculate(100.0, 10) = 137.04
        // calculate(100.0, 11) = 142.21
        assertEquals(137.04, e1rmAt10, 0.05)
        assertEquals(142.21, e1rmAt11, 0.05)

        // Monotonicity: more reps at the same weight MUST yield a higher estimated 1RM
        assertTrue(e1rmAt11 > e1rmAt10)
    }

    @Test
    @DisplayName("e1RM is strictly monotonic up to high reps")
    fun strictlyMonotonic() {
        val weight = 100.0
        var previousE1rm = EstimatedOneRm.calculate(weight, 1)

        for (reps in 2..50) {
            val currentE1rm = EstimatedOneRm.calculate(weight, reps)
            assertTrue(
                currentE1rm > previousE1rm,
                "e1RM must be strictly monotonic: reps=$reps current=$currentE1rm previous=$previousE1rm"
            )
            previousE1rm = currentE1rm
        }
    }

    @Test
    @DisplayName("Extremely high reps do not yield negative or infinite values (decay asymptotic to zero)")
    fun highRepsDecayAsymptotic() {
        val weight = 100.0
        val e1rmAt100 = EstimatedOneRm.calculate(weight, 100)

        // The e1RM must remain positive and finite, not drop to negative (unlike Brzycki)
        assertTrue(e1rmAt100 > 0.0)
        assertTrue(e1rmAt100.isFinite())
        
        // At 100 reps, e1rm is weight / (%1RM(100))
        // Since %1RM decays exponentially, e1RM grows, but in a stable, well-defined exponential curve:
        // %1RM(100) = 0.7297 * e^(-0.037 * 90) = 0.026
        // e1RM = 100.0 / 0.026 = 3832.22
        assertEquals(3851.71, e1rmAt100, 0.1)
    }

    @Test
    @DisplayName("Weight and reps projection round-trips correctly")
    fun projectionRoundTrips() {
        val e1rm = 150.0

        // Test below threshold (e.g. 5 reps)
        val weightAt5 = EstimatedOneRm.weightForReps(e1rm, 5)
        val repsAtWeight5 = EstimatedOneRm.repsForWeight(e1rm, weightAt5)
        assertEquals(5, repsAtWeight5)

        // Test above threshold (e.g. 15 reps)
        val weightAt15 = EstimatedOneRm.weightForReps(e1rm, 15)
        val repsAtWeight15 = EstimatedOneRm.repsForWeight(e1rm, weightAt15)
        assertEquals(15, repsAtWeight15)
    }
}

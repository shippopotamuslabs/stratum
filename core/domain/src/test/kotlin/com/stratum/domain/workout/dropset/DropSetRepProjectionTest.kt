package com.stratum.domain.workout.dropset

import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DropSetRepProjection: zero-rest residual on drop load")
class DropSetRepProjectionTest {

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    @Test
    @DisplayName("100×8@1RIR at 80 lb is ~half of fresh e1RM capacity, not near-fresh")
    fun zeroRestHalvesFreshCapacity() {
        val reps = DropSetRepProjection.expectedReps(
            priorWeight = lb(100.0),
            priorReps = 8,
            priorRir = 1,
            dropWeight = lb(80.0),
        )
        // Fresh at 80 after 100×9-fail ≈ 15; ×0.5 → 8
        assertEquals(8, reps)
        assertTrue(reps < 12, "must undercut mild normal-rest bump projections")
    }

    @Test
    @DisplayName("rirGoal subtracts from zero-rest capacity")
    fun rirGoalSubtracts() {
        val atZero = DropSetRepProjection.expectedRepsAtRir0(
            priorWeight = lb(100.0),
            priorReps = 8,
            priorRir = 1,
            dropWeight = lb(80.0),
        )
        val atOne = DropSetRepProjection.expectedReps(
            priorWeight = lb(100.0),
            priorReps = 8,
            priorRir = 1,
            dropWeight = lb(80.0),
            rirGoal = 1,
        )
        assertEquals((atZero - 1).coerceAtLeast(1), atOne)
    }

    @Test
    @DisplayName("80×6 → 64 keeps aggressive residual")
    fun secondDropFromLogged() {
        assertEquals(
            6,
            DropSetRepProjection.expectedReps(
                priorWeight = lb(80.0),
                priorReps = 6,
                priorRir = 0,
                dropWeight = lb(64.0),
            ),
        )
    }
}

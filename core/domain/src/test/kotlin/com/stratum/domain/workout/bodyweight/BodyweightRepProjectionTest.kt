package com.stratum.domain.workout.bodyweight

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BodyweightRepProjection] (STR-29): the load-free fatigue projection
 * that gives bodyweight straight sets live rep recommendations without e1RM-from-load.
 *
 * Slope units are reps per set index (the same unit every downstream consumer of
 * [com.stratum.domain.workout.fatigue.projectReps] expects): the projection decays the
 * performed failure-rep baseline (reps + RIR) by `slope * (setIndex - 1)`.
 */
@DisplayName("BodyweightRepProjection: load-free rep projection (STR-29)")
class BodyweightRepProjectionTest {

    @Test
    @DisplayName("Population slope decays failure-rep capacity across set indices")
    fun `population slope decays capacity across sets`() {
        // 12 reps @ RIR 1, no per-lift data → population slope (-2.0 reps/set).
        assertEquals(11, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 2, null, null))
        assertEquals(9, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 3, null, null))
        assertEquals(7, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 4, null, null))
    }

    @Test
    @DisplayName("expectedReps subtracts the RIR goal from failure capacity")
    fun `rir goal subtracted from failure capacity`() {
        // Baseline 13 @ RIR 0 on set 2 is 11; at RIR goal 1 it is 10.
        assertEquals(
            10,
            BodyweightRepProjection.expectedReps(12, 1, 2, null, null, rirGoal = 1),
        )
        assertEquals(
            8,
            BodyweightRepProjection.expectedReps(12, 1, 3, null, null, rirGoal = 1),
        )
    }

    @Test
    @DisplayName("Per-lift slope is honored when present and steep")
    fun `steep per-lift slope is honored`() {
        // Slope -3.0 reps/set: set 2 @ RIR 0 is 13 - 3 = 10.
        assertEquals(10, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 2, -3.0, 0.0))
        assertEquals(7, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 3, -3.0, 0.0))
    }

    @Test
    @DisplayName("Small per-lift slopes are honored as given (upstream nulls noise)")
    fun `small per-lift slope is honored as given`() {
        // The insights pipeline nulls |slope| < 0.5 upstream, so a small slope here
        // is a genuine measurement and is applied directly: 13 - 0.2 -> 13.
        assertEquals(13, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 2, -0.2, 0.0))
    }

    @Test
    @DisplayName("Positive slopes are coerced to zero (no capacity gain across sets)")
    fun `positive slope coerced to zero`() {
        // A positive slope (capacity "growing" with fatigue) is noise — projections
        // hold the baseline instead of increasing reps.
        assertEquals(13, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 2, 1.0, 0.0))
        assertEquals(13, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 3, 1.0, 0.0))
    }

    @Test
    @DisplayName("Intercept shifts the decay curve")
    fun `intercept shifts decay`() {
        // Intercept -1.0: set 2 @ RIR 0 is 13 - 2 - 1 = 10.
        assertEquals(10, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 2, -2.0, -1.0))
    }

    @Test
    @DisplayName("Null RIR is treated as zero")
    fun `null rir treated as zero`() {
        assertEquals(
            BodyweightRepProjection.expectedRepsAtRir0(12, 0, 2, null, null),
            BodyweightRepProjection.expectedRepsAtRir0(12, null, 2, null, null),
        )
    }

    @Test
    @DisplayName("Projection floors at one rep, never zero or negative")
    fun `projection floors at min reps`() {
        // 2 reps @ RIR 0 with population decay is deep underwater by set 5.
        assertEquals(1, BodyweightRepProjection.expectedRepsAtRir0(2, 0, 5, null, null))
        assertEquals(1, BodyweightRepProjection.expectedReps(2, 0, 5, null, null, rirGoal = 2))
    }

    @Test
    @DisplayName("Performed set is index 1: no decay applied to itself")
    fun `set index one has no decay`() {
        assertEquals(13, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 1, null, null))
        assertEquals(13, BodyweightRepProjection.expectedRepsAtRir0(12, 1, 1, -3.0, 0.0))
    }
}

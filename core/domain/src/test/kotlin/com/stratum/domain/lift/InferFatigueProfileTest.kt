package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * FPR-S1-013 … FPR-S1-017 — [inferFatigueProfile] rules (D8).
 * Production stub throws until Implementation ports [inferFatigueProfile] rules to fatigue.
 */
class InferFatigueProfileTest {

    @Test
    @DisplayName("FPR-S1-013 empty patterns infer Low")
    fun fprS1013_emptyPatternsInferLow() {
        assertEquals(
            FatigueProfile.Low,
            inferFatigueProfile(emptySet(), liftName = null),
        )
    }

    @Test
    @DisplayName("FPR-S1-014 isolation-style patterns infer Low")
    fun fprS1014_isolationPatternsInferLow() {
        assertEquals(
            FatigueProfile.Low,
            inferFatigueProfile(MovementPattern.ElbowExtension, "Skull Crusher"),
        )
        assertEquals(
            FatigueProfile.Low,
            inferFatigueProfile(MovementPattern.PlantarFlexion, "Calf Raise"),
        )
    }

    @Test
    @DisplayName("FPR-S1-015 free-weight axial squat or hinge infers High")
    fun fprS1015_freeWeightAxialInfersHigh() {
        assertEquals(
            FatigueProfile.High,
            inferFatigueProfile(MovementPattern.HipHinge, "Barbell Romanian Deadlift"),
        )
        assertEquals(
            FatigueProfile.High,
            inferFatigueProfile(MovementPattern.SquatPattern, "Barbell Back Squat"),
        )
    }

    @Test
    @DisplayName("FPR-S1-016 machine squat or non-free-weight hinge infers Medium")
    fun fprS1016_machineSquatInfersMedium() {
        assertEquals(
            FatigueProfile.Medium,
            inferFatigueProfile(MovementPattern.SquatPattern, "Smith Machine Back Squat"),
        )
        assertEquals(
            FatigueProfile.Medium,
            inferFatigueProfile(MovementPattern.HipHinge, "Weighted Roman Chair Hip Hinge"),
        )
    }

    @Test
    @DisplayName("FPR-S1-017 major multi-joint patterns infer Medium")
    fun fprS1017_majorPressPullInfersMedium() {
        assertEquals(
            FatigueProfile.Medium,
            inferFatigueProfile(MovementPattern.VerticalPull, "Overhand Grip Cable Lat Pulldown"),
        )
        assertEquals(
            FatigueProfile.Medium,
            inferFatigueProfile(MovementPattern.HorizontalPress, "Machine Chest Press"),
        )
    }
}

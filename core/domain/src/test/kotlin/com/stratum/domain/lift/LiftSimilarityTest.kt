package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LiftSimilarityTest {
    @Nested
    @DisplayName("liftSimilarity")
    inner class Scoring {
        @Test
        @DisplayName("identical lift scores 1.0")
        fun identical() {
            val lift = sampleLift(
                id = "bench-press",
                patterns = setOf(MovementPattern.HorizontalPress),
                primary = setOf(MuscleGroup.Chest),
            )
            assertEquals(1.0, liftSimilarity(lift, lift))
        }

        @Test
        @DisplayName("disjoint lifts score 0.0 — no shared patterns or muscles")
        fun disjoint() {
            val bench = sampleLift(
                id = "bench",
                patterns = setOf(MovementPattern.HorizontalPress),
                primary = setOf(MuscleGroup.Chest),
                fatigue = FatigueProfile.Medium,
                bias = MuscleLengthBias.MidRange,
            )
            val legCurl = sampleLift(
                id = "leg-curl",
                patterns = setOf(MovementPattern.KneeFlexion),
                primary = setOf(MuscleGroup.Hamstring),
                fatigue = FatigueProfile.Low,
                bias = MuscleLengthBias.Shortened,
            )
            assertEquals(0.0, liftSimilarity(bench, legCurl))
        }

        @Test
        @DisplayName("symmetric: similarity(a, b) == similarity(b, a)")
        fun symmetric() {
            val a = sampleLift(
                id = "front-squat",
                patterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipExtension),
                primary = setOf(MuscleGroup.Quad, MuscleGroup.Glute),
            )
            val b = sampleLift(
                id = "back-squat",
                patterns = setOf(MovementPattern.SquatPattern),
                primary = setOf(MuscleGroup.Quad),
            )
            assertEquals(liftSimilarity(a, b), liftSimilarity(b, a), 1e-9)
        }

        @Test
        @DisplayName("monotonic in shared movement patterns: more overlap scores higher")
        fun monotonicInPatterns() {
            val bench = sampleLift(
                id = "bench",
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                primary = setOf(MuscleGroup.Chest),
            )
            val closeOverlap = sampleLift(
                id = "close",
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                primary = setOf(MuscleGroup.Tricep),
            )
            val partialOverlap = sampleLift(
                id = "partial",
                patterns = setOf(MovementPattern.ElbowExtension),
                primary = setOf(MuscleGroup.Tricep),
            )
            val s1 = liftSimilarity(bench, closeOverlap)
            val s2 = liftSimilarity(bench, partialOverlap)
            assertTrue(s1 > s2, "Close pattern overlap ($s1) should beat partial ($s2)")
        }

        @Test
        @DisplayName("matching kinematic/length/category stack bonuses above baseline")
        fun bonusesStack() {
            val target = sampleLift(
                id = "target",
                patterns = setOf(MovementPattern.HorizontalPress),
                primary = setOf(MuscleGroup.Chest),
                fatigue = FatigueProfile.Medium,
                bias = MuscleLengthBias.Lengthened,
            )
            val sameClass = sampleLift(
                id = "same",
                patterns = setOf(MovementPattern.InclinePress),
                primary = setOf(MuscleGroup.Chest),
                fatigue = FatigueProfile.Medium,
                bias = MuscleLengthBias.Lengthened,
            )
            val differentClass = sampleLift(
                id = "diff",
                patterns = setOf(MovementPattern.InclinePress),
                primary = setOf(MuscleGroup.Chest),
                fatigue = FatigueProfile.Low,
                bias = MuscleLengthBias.Shortened,
            )
            assertTrue(
                liftSimilarity(target, sameClass) > liftSimilarity(target, differentClass),
                "Matching kinematic/length/category should score higher",
            )
        }

        @Test
        @DisplayName("clamped to 1.0 when bonuses exceed the ceiling")
        fun clamped() {
            // Identical in every dimension but a distinct id — already covered by identical == 1.0.
            // Construct near-identical with overlapping patterns/muscles + all bonuses and assert <= 1.0.
            val target = sampleLift(
                id = "a",
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                primary = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                secondary = setOf(MuscleGroup.AnteriorDeltoid),
                fatigue = FatigueProfile.Medium,
                bias = MuscleLengthBias.MidRange,
            )
            val candidate = sampleLift(
                id = "b",
                patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                primary = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                secondary = setOf(MuscleGroup.AnteriorDeltoid),
                fatigue = FatigueProfile.Medium,
                bias = MuscleLengthBias.MidRange,
            )
            assertTrue(liftSimilarity(target, candidate) <= 1.0)
        }
    }

    private fun sampleLift(
        id: String,
        patterns: Set<MovementPattern>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup> = emptySet(),
        fatigue: FatigueProfile = FatigueProfile.Low,
        bias: MuscleLengthBias = MuscleLengthBias.MidRange,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = id,
            movementPatterns = patterns,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            muscleLengthBias = bias,
            fatigueProfile = fatigue,
        )
}

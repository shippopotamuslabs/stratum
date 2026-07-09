package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LiftCategoryTest {
    @Nested
    @DisplayName("classifyLiftCategory")
    inner class Classify {
        @Test
        @DisplayName("compound upper: bench press (HorizontalPress + ElbowExtension + Chest)")
        fun compoundUpper() {
            assertEquals(
                LiftCategory.CompoundUpper,
                sampleLift(
                    patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                    primary = setOf(MuscleGroup.Chest),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("compound lower: back squat (SquatPattern + HipExtension + Quad)")
        fun compoundLower() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.Quad, MuscleGroup.Glute),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName(
            "compound lower: single SquatPattern + NonAxialCompound (hack squat / leg press style)",
        )
        fun compoundLowerViaKinematicProfile() {
            // Seed catalogs often store only SquatPattern on multi-joint machines; compound is
            // carried by kinematicProfile, not co-patterns.
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    kinematic = KinematicProfile.NonAxialCompound,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("compound lower: single SquatPattern + AxialCompound (barbell back squat)")
        fun compoundLowerViaAxialKinematic() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    kinematic = KinematicProfile.AxialCompound,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("lower isolation: single SquatPattern with Isolation kinematic stays isolation")
        fun singlePatternIsolationKinematicStaysIsolation() {
            assertEquals(
                LiftCategory.IsolationLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    kinematic = KinematicProfile.Isolation,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("upper isolation: bicep curl (ElbowFlexion + Bicep)")
        fun isolationUpper() {
            assertEquals(
                LiftCategory.IsolationUpper,
                sampleLift(patterns = setOf(MovementPattern.ElbowFlexion), primary = setOf(MuscleGroup.Bicep)).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("lower isolation: leg curl (KneeFlexion + Hamstring)")
        fun isolationLower() {
            assertEquals(
                LiftCategory.IsolationLower,
                sampleLift(patterns = setOf(MovementPattern.KneeFlexion), primary = setOf(MuscleGroup.Hamstring)).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("core: crunch with SpinalFlexion pattern")
        fun coreViaPattern() {
            assertEquals(
                LiftCategory.Core,
                sampleLift(patterns = setOf(MovementPattern.SpinalFlexion), primary = setOf(MuscleGroup.Ab)).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("core: reverse hyper with Ab/LowerBack primary and single pattern")
        fun coreViaMusclesOnly() {
            assertEquals(
                LiftCategory.Core,
                sampleLift(patterns = setOf(MovementPattern.HipExtension), primary = setOf(MuscleGroup.LowerBack, MuscleGroup.Ab)).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("deadlift (compound, hamstring/glute/lower-back) classifies CompoundLower, not Core")
        fun deadliftIsCompoundLowerNotCore() {
            // HipHinge + HipExtension = compound; lower-back secondary does not pull it into Core
            // because Core requires non-compound. Lower primary muscle wins -> CompoundLower.
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.Hamstring, MuscleGroup.Glute),
                    secondary = setOf(MuscleGroup.LowerBack),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("LiftCategory.displayName covers all five buckets")
        fun displayNames() {
            assertEquals("Compound Upper", LiftCategory.CompoundUpper.displayName)
            assertEquals("Compound Lower", LiftCategory.CompoundLower.displayName)
            assertEquals("Upper Isolation", LiftCategory.IsolationUpper.displayName)
            assertEquals("Lower Isolation", LiftCategory.IsolationLower.displayName)
            assertEquals("Core", LiftCategory.Core.displayName)
        }
    }

    private fun sampleLift(
        patterns: Set<MovementPattern>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup> = emptySet(),
        kinematic: KinematicProfile = KinematicProfile.Isolation,
    ): Lift =
        Lift(
            id = LiftId("sample-${primary.joinToString { it.name }}"),
            name = "Sample",
            movementPatterns = patterns,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            kinematicProfile = kinematic,
        )
}

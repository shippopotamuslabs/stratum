package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LiftCategoryTest {
    @Nested
    @DisplayName("categoryRegion exhaustiveness")
    inner class RegionExhaustiveness {
        @Test
        @DisplayName("every MovementPattern has a region (total when)")
        fun everyPatternHasRegion() {
            MovementPattern.entries.forEach { pattern ->
                // Accessing the property exercises the total when; failure = compile or missing branch
                assertTrue(
                    pattern.categoryRegion in LiftCategoryRegion.entries.toSet(),
                    pattern.name,
                )
            }
        }

        @Test
        @DisplayName("region partition covers all patterns without overlap")
        fun regionPartition() {
            val byRegion =
                MovementPattern.entries.groupBy { it.categoryRegion }
            assertEquals(8, byRegion[LiftCategoryRegion.Lower]?.size)
            assertEquals(1, byRegion[LiftCategoryRegion.Core]?.size)
            assertEquals(13, byRegion[LiftCategoryRegion.Upper]?.size)
            assertEquals(
                MovementPattern.entries.size,
                byRegion.values.sumOf { it.size },
            )
        }

        @ParameterizedTest
        @EnumSource(
            value = MovementPattern::class,
            names = [
                "SquatPattern",
                "HipHinge",
                "KneeExtension",
                "KneeFlexion",
                "PlantarFlexion",
                "HipExtension",
                "HipAbduction",
                "HipAdduction",
            ],
        )
        @DisplayName("lower patterns map to Lower")
        fun lowerPatterns(pattern: MovementPattern) {
            assertEquals(LiftCategoryRegion.Lower, pattern.categoryRegion)
        }

        @Test
        @DisplayName("SpinalFlexion maps to Core")
        fun corePattern() {
            assertEquals(LiftCategoryRegion.Core, MovementPattern.SpinalFlexion.categoryRegion)
        }
    }

    @Nested
    @DisplayName("classifyLiftCategory — compound")
    inner class Compound {
        @Test
        @DisplayName("compound upper: bench press (HorizontalPress + ElbowExtension + Chest)")
        fun compoundUpperBench() {
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
        fun compoundLowerSquatMultiPattern() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.Quad, MuscleGroup.Glute),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("compound lower: single SquatPattern + NonAxialCompound")
        fun compoundLowerViaFatigueProfile() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    fatigue = FatigueProfile.Medium,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("compound lower: single SquatPattern + AxialCompound")
        fun compoundLowerViaAxialKinematic() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("deadlift with ham/glute primary is CompoundLower, not Core")
        fun deadliftHamGlutePrimary() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.Hamstring, MuscleGroup.Glute),
                    secondary = setOf(MuscleGroup.LowerBack),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("deadlift with LowerBack primary is CompoundLower (pattern region), not CompoundUpper")
        fun deadliftLowerBackPrimary() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.LowerBack),
                    secondary = setOf(MuscleGroup.Glute, MuscleGroup.Hamstring),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("rack pull style LowerBack primary + hinge is CompoundLower")
        fun rackPullLowerBackPrimary() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.LowerBack),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("back extension compound (hinge + HipExtension, LowerBack primary) is CompoundLower")
        fun backExtensionCompoundLower() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.LowerBack),
                    fatigue = FatigueProfile.Medium,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("inherent compound: single HorizontalPress + Low fatigue is CompoundUpper")
        fun inherentCompoundHorizontalPress() {
            assertEquals(
                LiftCategory.CompoundUpper,
                sampleLift(
                    patterns = setOf(MovementPattern.HorizontalPress),
                    primary = setOf(MuscleGroup.Chest),
                    fatigue = FatigueProfile.Low,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("inherent compound: single VerticalPull + Isolation is CompoundUpper")
        fun inherentCompoundVerticalPull() {
            assertEquals(
                LiftCategory.CompoundUpper,
                sampleLift(
                    patterns = setOf(MovementPattern.VerticalPull),
                    primary = setOf(MuscleGroup.Back),
                    fatigue = FatigueProfile.Low,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("inherent compound: single SquatPattern + Isolation is CompoundLower")
        fun inherentCompoundSquatIsolationKinematic() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = setOf(MuscleGroup.Quad),
                    fatigue = FatigueProfile.Low,
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("empty primary + SquatPattern + Axial is CompoundLower via pattern region")
        fun emptyPrimarySquatAxial() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.SquatPattern),
                    primary = emptySet(),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }
    }

    @Nested
    @DisplayName("classifyLiftCategory — isolation")
    inner class Isolation {
        @Test
        @DisplayName("upper isolation: bicep curl")
        fun isolationUpperCurl() {
            assertEquals(
                LiftCategory.AccessoryUpper,
                sampleLift(
                    patterns = setOf(MovementPattern.ElbowFlexion),
                    primary = setOf(MuscleGroup.Bicep),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("lower isolation: leg curl")
        fun isolationLowerLegCurl() {
            assertEquals(
                LiftCategory.AccessoryLower,
                sampleLift(
                    patterns = setOf(MovementPattern.KneeFlexion),
                    primary = setOf(MuscleGroup.Hamstring),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("HipAbduction + Abductor primary is AccessoryLower")
        fun hipAbductionAccessoryLower() {
            assertEquals(
                LiftCategory.AccessoryLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipAbduction),
                    primary = setOf(MuscleGroup.Abductor),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("empty primary + ElbowFlexion is AccessoryUpper")
        fun emptyPrimaryElbowFlexion() {
            assertEquals(
                LiftCategory.AccessoryUpper,
                sampleLift(
                    patterns = setOf(MovementPattern.ElbowFlexion),
                    primary = emptySet(),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("empty primary + empty patterns defaults to AccessoryUpper")
        fun emptyMetadataDefaultsAccessoryUpper() {
            assertEquals(
                LiftCategory.AccessoryUpper,
                sampleLift(
                    patterns = emptySet(),
                    primary = emptySet(),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("secondary lower only (no primary lower) resolves AccessoryLower")
        fun secondaryLowerWeakSignal() {
            assertEquals(
                LiftCategory.AccessoryLower,
                sampleLift(
                    patterns = emptySet(),
                    primary = emptySet(),
                    secondary = setOf(MuscleGroup.Glute),
                ).classifyLiftCategory(),
            )
        }
    }

    @Nested
    @DisplayName("classifyLiftCategory — core")
    inner class Core {
        @Test
        @DisplayName("core: crunch with SpinalFlexion pattern")
        fun coreViaPattern() {
            assertEquals(
                LiftCategory.Core,
                sampleLift(
                    patterns = setOf(MovementPattern.SpinalFlexion),
                    primary = setOf(MuscleGroup.Ab),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("core: reverse hyper style Ab/LowerBack primary + HipExtension isolation")
        fun coreViaMusclesOnly() {
            assertEquals(
                LiftCategory.Core,
                sampleLift(
                    patterns = setOf(MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.LowerBack, MuscleGroup.Ab),
                ).classifyLiftCategory(),
            )
        }

        @Test
        @DisplayName("compound with LowerBack-only primary is not Core")
        fun compoundLowerBackNotCore() {
            assertEquals(
                LiftCategory.CompoundLower,
                sampleLift(
                    patterns = setOf(MovementPattern.HipHinge, MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.LowerBack),
                    fatigue = FatigueProfile.High,
                ).classifyLiftCategory(),
            )
        }
    }

    @Nested
    @DisplayName("isCompoundForCategory")
    inner class CompoundForCategory {
        @Test
        @DisplayName("multi-pattern is compound for category")
        fun multiPattern() {
            assertTrue(
                sampleLift(
                    patterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
                    primary = setOf(MuscleGroup.Chest),
                ).isCompoundForCategory(),
            )
        }

        @Test
        @DisplayName("inherent compound pattern is compound for category even with Low fatigue")
        fun inherentPattern() {
            assertTrue(
                sampleLift(
                    patterns = setOf(MovementPattern.VerticalPull),
                    primary = setOf(MuscleGroup.Back),
                    fatigue = FatigueProfile.Low,
                ).isCompoundForCategory(),
            )
        }

        @Test
        @DisplayName("ElbowFlexion alone is not compound for category")
        fun elbowFlexionNotInherent() {
            assertEquals(
                false,
                sampleLift(
                    patterns = setOf(MovementPattern.ElbowFlexion),
                    primary = setOf(MuscleGroup.Bicep),
                ).isCompoundForCategory(),
            )
        }

        @Test
        @DisplayName("HipExtension alone is not inherent compound")
        fun hipExtensionNotInherent() {
            assertEquals(
                false,
                sampleLift(
                    patterns = setOf(MovementPattern.HipExtension),
                    primary = setOf(MuscleGroup.Glute),
                ).isCompoundForCategory(),
            )
        }
    }

    @Nested
    @DisplayName("displayName")
    inner class DisplayNameLabels {
        @Test
        @DisplayName("covers all five buckets")
        fun coversAllFiveBuckets() {
            assertEquals("Compound Upper", LiftCategory.CompoundUpper.displayName)
            assertEquals("Compound Lower", LiftCategory.CompoundLower.displayName)
            assertEquals("Upper Accessory", LiftCategory.AccessoryUpper.displayName)
            assertEquals("Lower Accessory", LiftCategory.AccessoryLower.displayName)
            assertEquals("Core", LiftCategory.Core.displayName)
        }

        @Test
        @DisplayName("FPR-S4-003 AccessoryUpper display name is Upper Accessory")
        fun fprS4003_accessoryUpperDisplayName() {
            val accessoryUpper =
                LiftCategory.entries.single { it.name == "AccessoryUpper" }
            assertEquals("Upper Accessory", accessoryUpper.displayName)
        }

        @Test
        @DisplayName("FPR-S4-004 AccessoryLower display name is Lower Accessory")
        fun fprS4004_accessoryLowerDisplayName() {
            val accessoryLower =
                LiftCategory.entries.single { it.name == "AccessoryLower" }
            assertEquals("Lower Accessory", accessoryLower.displayName)
        }
    }

    @Nested
    @DisplayName("MAJOR_COMPOUND_PATTERNS")
    inner class MajorCompound {
        @Test
        @DisplayName("canonical set is the seven multi-joint families")
        fun membership() {
            assertEquals(
                setOf(
                    MovementPattern.SquatPattern,
                    MovementPattern.HipHinge,
                    MovementPattern.HorizontalPress,
                    MovementPattern.InclinePress,
                    MovementPattern.VerticalPress,
                    MovementPattern.HorizontalPull,
                    MovementPattern.VerticalPull,
                ),
                MAJOR_COMPOUND_PATTERNS,
            )
        }

        @Test
        @DisplayName("HipExtension is not major compound")
        fun hipExtensionExcluded() {
            assertFalse(MovementPattern.HipExtension in MAJOR_COMPOUND_PATTERNS)
        }
    }

    private fun sampleLift(
        patterns: Set<MovementPattern>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup> = emptySet(),
        fatigue: FatigueProfile = FatigueProfile.Low,
    ): Lift =
        Lift(
            id = LiftId("sample-${primary.joinToString { it.name }}-${patterns.joinToString { it.name }}"),
            name = "Sample",
            movementPatterns = patterns,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            fatigueProfile = fatigue,
        )
}

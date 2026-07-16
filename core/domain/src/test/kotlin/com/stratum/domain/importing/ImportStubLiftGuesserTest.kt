package com.stratum.domain.importing

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ImportStubLiftGuesserTest {
    @Test
    @DisplayName("Given row exercise name when guessing pattern then horizontal pull is returned")
    fun rowNameGuessesHorizontalPull() {
        assertEquals(
            MovementPattern.HorizontalPull,
            ImportStubLiftGuesser.guessMovementPattern("Iso-Lateral Row"),
        )
    }

    @Test
    @DisplayName("Given stub row definition when building then back primary and bicep secondary are set")
    fun rowDefinitionIncludesPullSecondaries() {
        val definition =
            ImportStubLiftGuesser.definitionFromName(
                name = "Leverage High Row",
                increment = Weight(5.0, Weight.WeightUnit.Lb),
            )

        assertEquals(MovementPattern.HorizontalPull, definition.movementPatterns.first())
        assertEquals(setOf(MuscleGroup.Back), definition.primaryMuscles)
        assertEquals(setOf(MuscleGroup.Bicep), definition.secondaryMuscles)
    }

    @Test
    @DisplayName("Given lateral raise names when guessing then ShoulderAbduction and LateralDeltoid are used")
    fun lateralRaiseNamesMapToSideDelts() {
        val names =
            listOf(
                "Single Arm Cable Cuffed Lateral Raise",
                "Seated Dumbbell Lateral Raise",
                "Behind-The-Back Cable Cuffed Y-Raise",
                "Seated Machine Lateral Raise",
            )
        names.forEach { name ->
            assertEquals(MovementPattern.ShoulderAbduction, ImportStubLiftGuesser.guessMovementPattern(name), name)
            val definition =
                ImportStubLiftGuesser.definitionFromName(
                    name = name,
                    increment = Weight(5.0, Weight.WeightUnit.Lb),
                )
            assertEquals(setOf(MuscleGroup.LateralDeltoid), definition.primaryMuscles, name)
        }
    }

    @Test
    @DisplayName("Given rear delt fly names when guessing then ShoulderHorizontalAbduction and PosteriorDeltoid are used")
    fun rearDeltFlyNamesMapToRearDelts() {
        val names =
            listOf(
                "45° Cable Rear Delt Fly",
                "Single Arm 45° Cable Rear Delt Fly",
                "Neutral Grip Machine Rear Delt Fly",
            )
        names.forEach { name ->
            assertEquals(MovementPattern.ShoulderHorizontalAbduction, ImportStubLiftGuesser.guessMovementPattern(name), name)
            val definition =
                ImportStubLiftGuesser.definitionFromName(
                    name = name,
                    increment = Weight(5.0, Weight.WeightUnit.Lb),
                )
            assertEquals(setOf(MuscleGroup.PosteriorDeltoid), definition.primaryMuscles, name)
        }
    }

    @Test
    @DisplayName("Given misclassified custom lateral raise when reading effective metadata then side delts are recovered")
    fun recoversMisclassifiedLateralRaiseCustom() {
        val lift =
            Lift(
                id = LiftId("custom-lateral"),
                name = "Single Arm Cable Cuffed Lateral Raise",
                movementPatterns = setOf(MovementPattern.SquatPattern),
                primaryMuscles = setOf(MuscleGroup.Quad),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isCustom = true,
            )

        val pattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
        val primary = ImportStubLiftGuesser.effectivePrimaryMuscles(lift, pattern)

        assertEquals(MovementPattern.ShoulderAbduction, pattern)
        assertEquals(setOf(MuscleGroup.LateralDeltoid), primary)
    }

    @Test
    @DisplayName("Given misclassified custom rear delt fly when reading effective metadata then rear delts are recovered")
    fun recoversMisclassifiedRearDeltFlyCustom() {
        val lift =
            Lift(
                id = LiftId("custom-rear-delt"),
                name = "45° Cable Rear Delt Fly",
                movementPatterns = setOf(MovementPattern.ShoulderAbduction),
                primaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
                secondaryMuscles = emptySet(),
                incrementTiers = emptyList(),
                presetTags = emptySet(),
                enabled = true,
                isCustom = true,
            )

        val pattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
        val primary = ImportStubLiftGuesser.effectivePrimaryMuscles(lift, pattern)

        assertEquals(MovementPattern.ShoulderHorizontalAbduction, pattern)
        assertEquals(setOf(MuscleGroup.PosteriorDeltoid), primary)
    }

    @Test
    @DisplayName("Given chest dip vs triceps dip names when guessing then press vs extension patterns apply")
    fun dipNamesDistinguishChestAndTriceps() {
        assertEquals(
            MovementPattern.HorizontalPress,
            ImportStubLiftGuesser.guessMovementPattern("Chest Dip"),
        )
        assertEquals(
            MovementPattern.ElbowExtension,
            ImportStubLiftGuesser.guessMovementPattern("Triceps Dip"),
        )
    }

    @Test
    @DisplayName("Given crunch and sit-up names when guessing then SpinalFlexion and Ab are used")
    fun crunchNamesMapToAbs() {
        val names = listOf("Machine Crunch", "Cable Crunch", "Decline Sit-Up", "Ab Roller")
        names.forEach { name ->
            assertEquals(
                MovementPattern.SpinalFlexion,
                ImportStubLiftGuesser.guessMovementPattern(name),
                name,
            )
            val meta = checkNotNull(ImportStubLiftGuesser.inferredMetadataFromName(name))
            assertEquals(setOf(MuscleGroup.Ab), meta.primaryMuscles, name)
        }
    }

    @Test
    @DisplayName("Given hip hinge names when guessing then HipHinge and glute/hamstring are used")
    fun hipHingeNamesMapToHingeMuscles() {
        val names =
            listOf(
                "Weighted Roman Chair Hip Hinge",
                "Roman Chair Hip Hinge",
                "45° Back Extension",
                "Barbell Romanian Deadlift",
            )
        names.forEach { name ->
            assertEquals(
                MovementPattern.HipHinge,
                ImportStubLiftGuesser.guessMovementPattern(name),
                name,
            )
            val meta = checkNotNull(ImportStubLiftGuesser.inferredMetadataFromName(name))
            assertEquals(
                setOf(MuscleGroup.Glute, MuscleGroup.Hamstring),
                meta.primaryMuscles,
                name,
            )
        }
    }

    @Test
    @DisplayName("Given leg curl name when guessing then KneeFlexion not ElbowFlexion")
    fun legCurlIsNotBicepsCurl() {
        assertEquals(
            MovementPattern.KneeFlexion,
            ImportStubLiftGuesser.guessMovementPattern("Lying Leg Curl"),
        )
        assertEquals(
            setOf(MuscleGroup.Hamstring),
            ImportStubLiftGuesser.primaryMusclesFor(MovementPattern.KneeFlexion),
        )
    }

    @Test
    @DisplayName("Given ambiguous brand name when guessing then null so LLM residual path is used")
    fun ambiguousBrandNameReturnsNull() {
        assertEquals(null, ImportStubLiftGuesser.guessMovementPattern("Katana Special"))
        assertEquals(null, ImportStubLiftGuesser.inferredMetadataFromName("Katana Special"))
    }
}

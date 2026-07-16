package com.stratum.domain.importing

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class InferredCustomLiftMetadataTest {
    @Test
    @DisplayName("Seed loading maps to equipmentId only")
    fun seedLoadingToDefinition() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Seated Neutral Grip Plate-Loaded Machine Press",
                movementPattern = MovementPattern.HorizontalPress,
                primaryMuscles = setOf(MuscleGroup.Chest),
                loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.DoubleWeightPlates),
            ).toCustomLiftDefinition()

        assertEquals(SeedEquipmentIds.DoubleWeightPlates, definition.equipmentId)
        assertNull(definition.equipmentName)
        assertNull(definition.equipmentCreateIncrement)
        assertNull(definition.increment)
    }

    @Test
    @DisplayName("Custom loading maps to equipmentName + equipmentCreateIncrement, not lift increment")
    fun customLoadingToDefinition() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Pro2 Pec Fly",
                movementPattern = MovementPattern.HorizontalPress,
                primaryMuscles = setOf(MuscleGroup.Chest),
                loading =
                    InferredEquipmentLoading.Custom(
                        name = "Life Fitness Pro2 Stack",
                        incrementLb = 12.5,
                    ),
            ).toCustomLiftDefinition()

        assertNull(definition.equipmentId)
        assertEquals("Life Fitness Pro2 Stack", definition.equipmentName)
        assertEquals(Weight(12.5, Weight.WeightUnit.Lb), definition.equipmentCreateIncrement)
        assertNull(definition.increment)
    }

    @Test
    @DisplayName("Null loading maps to shared Default equipment at +5")
    fun nullLoadingUsesDefault() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Mystery",
                movementPattern = MovementPattern.ElbowExtension,
                primaryMuscles = setOf(MuscleGroup.Tricep),
                loading = null,
            ).toCustomLiftDefinition()

        assertNull(definition.equipmentId)
        assertEquals(InferredEquipmentLoading.DEFAULT_EQUIPMENT_NAME, definition.equipmentName)
        assertEquals(
            Weight(InferredEquipmentLoading.DEFAULT_INCREMENT_LB, Weight.WeightUnit.Lb),
            definition.equipmentCreateIncrement,
        )
    }

    @Test
    @DisplayName("Blank custom name becomes Default")
    fun blankCustomNameUsesDefault() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Mystery",
                movementPattern = MovementPattern.ElbowExtension,
                primaryMuscles = setOf(MuscleGroup.Tricep),
                loading = InferredEquipmentLoading.Custom(name = "  ", incrementLb = 5.0),
            ).toCustomLiftDefinition()

        assertEquals(InferredEquipmentLoading.DEFAULT_EQUIPMENT_NAME, definition.equipmentName)
    }

    @Test
    @DisplayName("Horizontal press custom lift is NonAxialCompound, not Isolation")
    fun pressInfersNonAxialCompound() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Seated Neutral Grip Plate-Loaded Machine Chest Press",
                movementPattern = MovementPattern.HorizontalPress,
                primaryMuscles = setOf(MuscleGroup.Chest),
                loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.DoubleWeightPlates),
            ).toCustomLiftDefinition()
        assertEquals(FatigueProfile.Medium, definition.fatigueProfile)
    }

    @Test
    @DisplayName("Vertical pull custom lift is NonAxialCompound")
    fun pulldownInfersNonAxialCompound() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Overhand Grip Cable Lat Pulldown",
                movementPattern = MovementPattern.VerticalPull,
                primaryMuscles = setOf(MuscleGroup.Back),
                loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.Cable),
            ).toCustomLiftDefinition()
        assertEquals(FatigueProfile.Medium, definition.fatigueProfile)
    }

    @Test
    @DisplayName("Barbell RDL name + HipHinge is AxialCompound")
    fun barbellRdlInfersAxial() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Barbell Romanian Deadlift",
                movementPattern = MovementPattern.HipHinge,
                primaryMuscles = setOf(MuscleGroup.Hamstring),
                loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.DoubleWeightPlates),
            ).toCustomLiftDefinition()
        assertEquals(FatigueProfile.High, definition.fatigueProfile)
    }

    @Test
    @DisplayName("Elbow extension isolation stays Isolation")
    fun tricepIsoStaysIsolation() {
        val definition =
            InferredCustomLiftMetadata(
                exerciseNameRaw = "Cable Rope Triceps Pushdown",
                movementPattern = MovementPattern.ElbowExtension,
                primaryMuscles = setOf(MuscleGroup.Tricep),
                loading = InferredEquipmentLoading.Seed(SeedEquipmentIds.Cable),
            ).toCustomLiftDefinition()
        assertEquals(FatigueProfile.Low, definition.fatigueProfile)
    }
}

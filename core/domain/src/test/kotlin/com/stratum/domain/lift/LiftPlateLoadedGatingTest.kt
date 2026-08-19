package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Lift.isPlateLoaded equipment gating tests")
class LiftPlateLoadedGatingTest {

    private fun createLift(
        name: String,
        equipmentId: EquipmentId?,
        loadType: LoadType = LoadType.Weighted,
    ): Lift = Lift(
        id = LiftId("test-lift"),
        name = name,
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        equipmentId = equipmentId,
        loadType = loadType,
        enabled = true,
    )

    @Test
    @DisplayName("DoubleWeightPlates on weighted lift is plate-loaded")
    fun doubleWeightPlatesIsPlateLoaded() {
        val lift = createLift(
            name = "Barbell Bench Press",
            equipmentId = SeedEquipmentIds.DoubleWeightPlates,
            loadType = LoadType.Weighted,
        )
        assertTrue(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("SingleWeightPlate on weighted lift is plate-loaded")
    fun singleWeightPlateIsPlateLoaded() {
        val lift = createLift(
            name = "Plate Loaded Hip Thrust",
            equipmentId = SeedEquipmentIds.SingleWeightPlate,
            loadType = LoadType.Weighted,
        )
        assertTrue(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("Dumbbell bench press is not plate-loaded despite bench press in name")
    fun dumbbellBenchPressIsNotPlateLoaded() {
        val lift = createLift(
            name = "Dumbbell Bench Press",
            equipmentId = SeedEquipmentIds.Dumbbell,
            loadType = LoadType.Weighted,
        )
        assertFalse(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("Goblet squat is not plate-loaded despite squat in name")
    fun gobletSquatIsNotPlateLoaded() {
        val lift = createLift(
            name = "Goblet Squat",
            equipmentId = SeedEquipmentIds.Kettlebell,
            loadType = LoadType.Weighted,
        )
        assertFalse(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("Cable crossover is not plate-loaded")
    fun cableCrossoverIsNotPlateLoaded() {
        val lift = createLift(
            name = "Cable Crossover",
            equipmentId = SeedEquipmentIds.Cable,
            loadType = LoadType.Weighted,
        )
        assertFalse(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("Pin loaded machine chest press is not plate-loaded")
    fun pinLoadedChestPressIsNotPlateLoaded() {
        val lift = createLift(
            name = "Chest Press Machine",
            equipmentId = SeedEquipmentIds.PinLoadedMachine,
            loadType = LoadType.Weighted,
        )
        assertFalse(lift.isPlateLoaded)
    }

    @Test
    @DisplayName("Bodyweight pull up is not plate-loaded")
    fun bodyweightPullUpIsNotPlateLoaded() {
        val lift = createLift(
            name = "Pull Up",
            equipmentId = null,
            loadType = LoadType.Bodyweight,
        )
        assertFalse(lift.isPlateLoaded)
        assertFalse(lift.isDoubleSidedPlateLoad)
    }

    @Test
    @DisplayName("DoubleWeightPlates is double-sided")
    fun doubleWeightPlatesIsDoubleSided() {
        val lift = createLift(
            name = "Barbell Bench Press",
            equipmentId = SeedEquipmentIds.DoubleWeightPlates,
        )
        assertTrue(lift.isDoubleSidedPlateLoad)
    }

    @Test
    @DisplayName("SingleWeightPlate is plate-loaded but not double-sided")
    fun singleWeightPlateIsNotDoubleSided() {
        val lift = createLift(
            name = "Plate Loaded Hip Thrust",
            equipmentId = SeedEquipmentIds.SingleWeightPlate,
        )
        assertTrue(lift.isPlateLoaded)
        assertFalse(lift.isDoubleSidedPlateLoad)
    }
}

package com.stratum.feature.workout.ui

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.RenderedSet
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import com.stratum.domain.workout.SetSlotId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WorkoutUiMapper")
class WorkoutUiMapperTest {

    @Test
    @DisplayName("multi-word names use first letters of first two words")
    fun multiWordInitials() {
        assertEquals("BB", WorkoutUiMapper.liftNavigatorInitials("Barbell Bench Press"))
        assertEquals("DR", WorkoutUiMapper.liftNavigatorInitials("Dumbbell Row"))
    }

    @Test
    @DisplayName("single-word names use first two letters")
    fun singleWordInitials() {
        assertEquals("SQ", WorkoutUiMapper.liftNavigatorInitials("Squat"))
        assertEquals("DE", WorkoutUiMapper.liftNavigatorInitials("Deadlift"))
    }

    @Test
    @DisplayName("blank name yields empty initials")
    fun blankName() {
        assertEquals("", WorkoutUiMapper.liftNavigatorInitials("   "))
    }

    @Test
    @DisplayName("all pinned workout and lift notes copy as presentation lists")
    fun pinnedNotesCopyToUi() {
        val rendered =
            RenderedWorkout(
                workoutId = WorkoutId("w-1"),
                workoutName = "Lower",
                lifts =
                    listOf(
                        RenderedWorkoutLift(
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            liftId = LiftId("lift-1"),
                            liftName = "Squat",
                            progressionScheme = ProgressionScheme.Double,
                            sets = emptyList(),
                            loadType = LoadType.Weighted,
                            workoutPinnedNotes = listOf("Elbows in", "Chest up"),
                            liftPinnedNotes = listOf("Belt on"),
                        ),
                    ),
            )
        val mapped = WorkoutUiMapper.map(rendered, sessionStartedAtEpochMilli = 1L)
        assertEquals(listOf("Elbows in", "Chest up"), mapped.lifts.single().workoutPinnedNotes)
        assertEquals(listOf("Belt on"), mapped.lifts.single().liftPinnedNotes)
    }

    @Test
    @DisplayName("equipment join populates the edit-equipment routing fields")
    fun equipmentJoinMapsRoutingFields() {
        val rendered =
            RenderedWorkout(
                workoutId = WorkoutId("w-1"),
                workoutName = "Lower",
                lifts =
                    listOf(
                        RenderedWorkoutLift(
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            liftId = LiftId("lift-1"),
                            liftName = "Squat",
                            progressionScheme = ProgressionScheme.Double,
                            sets = emptyList(),
                            loadType = LoadType.Weighted,
                        ),
                        RenderedWorkoutLift(
                            workoutLiftId = WorkoutLiftId("wl-2"),
                            liftId = LiftId("lift-2"),
                            liftName = "Curl",
                            progressionScheme = ProgressionScheme.Double,
                            sets = emptyList(),
                            loadType = LoadType.Weighted,
                        ),
                    ),
            )
        val mapped =
            WorkoutUiMapper.map(
                rendered,
                sessionStartedAtEpochMilli = 1L,
                equipmentEditTargetByLiftId =
                    mapOf(
                        "lift-1" to EquipmentEditTarget(
                            equipmentId = "eq-1",
                            hasIncrementOverride = false,
                        ),
                    ),
            )
        val (inheriting, unjoined) = mapped.lifts
        assertEquals("eq-1", inheriting.equipmentId)
        assertFalse(inheriting.hasIncrementOverride)
        assertEquals(null, unjoined.equipmentId)
        assertFalse(unjoined.hasIncrementOverride)
    }

    @Test
    @DisplayName("barbell and plate loaded lifts set isPlateLoaded to true")
    fun barbellLiftsMapIsPlateLoadedTrue() {
        val workout = RenderedWorkout(
            workoutId = WorkoutId("w-1"),
            workoutName = "Push Day",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("l-1"),
                    liftName = "Barbell Bench Press",
                    progressionScheme = ProgressionScheme.Double,
                    loadType = LoadType.Weighted,
                    isPlateLoaded = true,
                    sets = listOf(
                        RenderedSet(
                            slotId = SetSlotId.Template(LiftSetId("ls-1")),
                            workoutLiftId = WorkoutLiftId("wl-1"),
                            liftId = LiftId("l-1"),
                            position = 0,
                            setType = SetType.Standard,
                            isTopSet = true,
                            repMin = 6,
                            repMax = 10,
                            rirGoal = 2,
                            restSeconds = 120,
                            recommendedWeight = Weight(225.0, Weight.WeightUnit.Lb),
                            recommendedReps = 8,
                            loggedValue = null,
                        ),
                    ),
                ),
            ),
        )

        val result = WorkoutUiMapper.map(
            rendered = workout,
            sessionStartedAtEpochMilli = 1000L,
            displayUnit = Weight.WeightUnit.Lb,
        )

        assertTrue(result.lifts[0].isPlateLoaded)
        assertTrue(result.lifts[0].sets[0].isPlateLoaded)
        assertTrue(result.lifts[0].isDoubleSided)
        assertEquals("lb", result.lifts[0].unitLabel)
    }

    @Test
    @DisplayName("bodyweight and cable lifts set isPlateLoaded to false")
    fun nonPlateLiftsMapIsPlateLoadedFalse() {
        val workout = RenderedWorkout(
            workoutId = WorkoutId("w-1"),
            workoutName = "Push Day",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = WorkoutLiftId("wl-2"),
                    liftId = LiftId("l-2"),
                    liftName = "Push Up",
                    progressionScheme = ProgressionScheme.Double,
                    loadType = LoadType.Bodyweight,
                    sets = emptyList(),
                ),
            ),
        )

        val result = WorkoutUiMapper.map(
            rendered = workout,
            sessionStartedAtEpochMilli = 1000L,
            displayUnit = Weight.WeightUnit.Lb,
        )

        assertFalse(result.lifts[0].isPlateLoaded)
        assertFalse(result.lifts[0].showWeightColumn)
        assertEquals("", result.lifts[0].weightHeaderLabel)
    }

    @Test
    @DisplayName("bodyweight lifts hide the weight column and do not require weight input")
    fun bodyweightHidesWeightColumnAndInput() {
        val workout = RenderedWorkout(
            workoutId = WorkoutId("w-1"),
            workoutName = "Pull Day",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = WorkoutLiftId("wl-bw"),
                    liftId = LiftId("l-bw"),
                    liftName = "Pull Up",
                    progressionScheme = ProgressionScheme.Double,
                    loadType = LoadType.Bodyweight,
                    sets = listOf(
                        RenderedSet(
                            slotId = SetSlotId.Template(LiftSetId("ls-bw")),
                            workoutLiftId = WorkoutLiftId("wl-bw"),
                            liftId = LiftId("l-bw"),
                            position = 0,
                            setType = SetType.Standard,
                            isTopSet = true,
                            repMin = 8,
                            repMax = 12,
                            rirGoal = 2,
                            restSeconds = 90,
                            recommendedWeight = Weight(0.0, Weight.WeightUnit.Lb),
                            recommendedReps = 10,
                            loggedValue = null,
                            previousWeight = Weight(0.0, Weight.WeightUnit.Lb),
                            previousReps = 8,
                            previousRir = 2,
                        ),
                    ),
                ),
            ),
        )

        val result = WorkoutUiMapper.map(
            rendered = workout,
            sessionStartedAtEpochMilli = 1000L,
            displayUnit = Weight.WeightUnit.Lb,
        )
        val lift = result.lifts.single()
        val set = lift.sets.single()

        assertFalse(lift.showWeightColumn)
        assertEquals("", lift.weightHeaderLabel)
        assertFalse(set.showWeightInput)
        assertEquals("", set.recommendedWeightLabel)
        assertEquals("8", set.previousWeightLabel)
        assertEquals("2 RIR", set.previousRirLabel)
    }

    @Test
    @DisplayName("single-sleeve plate lifts map isDoubleSided to false")
    fun singleSleeveMapsIsDoubleSidedFalse() {
        val workout = RenderedWorkout(
            workoutId = WorkoutId("w-1"),
            workoutName = "Push Day",
            lifts = listOf(
                RenderedWorkoutLift(
                    workoutLiftId = WorkoutLiftId("wl-3"),
                    liftId = LiftId("l-3"),
                    liftName = "Plate Loaded Hip Thrust",
                    progressionScheme = ProgressionScheme.Double,
                    loadType = LoadType.Weighted,
                    isPlateLoaded = true,
                    isDoubleSided = false,
                    sets = emptyList(),
                ),
            ),
        )

        val result = WorkoutUiMapper.map(
            rendered = workout,
            sessionStartedAtEpochMilli = 1000L,
            displayUnit = Weight.WeightUnit.Lb,
        )

        assertTrue(result.lifts[0].isPlateLoaded)
        assertFalse(result.lifts[0].isDoubleSided)
    }
}

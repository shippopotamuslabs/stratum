package com.stratum.feature.workout.ui

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.RenderedWorkout
import com.stratum.domain.workout.RenderedWorkoutLift
import org.junit.jupiter.api.Assertions.assertEquals
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
    @DisplayName("pinned workout and lift notes copy as presentation strings")
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
                            workoutPinnedNote = "Elbows in",
                            liftPinnedNote = "Belt on",
                        ),
                    ),
            )
        val mapped = WorkoutUiMapper.map(rendered, sessionStartedAtEpochMilli = 1L)
        assertEquals("Elbows in", mapped.lifts.single().workoutPinnedNote)
        assertEquals("Belt on", mapped.lifts.single().liftPinnedNote)
    }
}


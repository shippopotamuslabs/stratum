package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MergeRenderedWorkoutPinnedNoteTest {

    @Test
    @DisplayName("overlay workout pin wins; structure catalog pin is copied through")
    fun overlayWorkoutPinWins() {
        val workoutLiftId = WorkoutLiftId("wl-1")
        val liftId = LiftId("lift-1")
        val structure =
            WorkoutStructureProjection(
                workoutId = WorkoutId("w-1"),
                workoutName = "Day",
                lifts =
                    listOf(
                        WorkoutLiftProjection(
                            workoutLiftId = workoutLiftId,
                            liftId = liftId,
                            liftName = "Squat",
                            progressionScheme = ProgressionScheme.Double,
                            slots = emptyList(),
                            workoutPinnedNote = "template",
                            liftPinnedNote = "catalog",
                        ),
                    ),
            )
        val overlay =
            WorkoutOverlay(
                workoutLiftPinnedNotes = mapOf(workoutLiftId to "overlay"),
            )
        val rendered =
            MergeRenderedWorkoutUseCase()(
                structure,
                ProgressionSnapshot(WorkoutId("w-1"), emptyMap()),
                overlay,
            )
        assertEquals("overlay", rendered.lifts.single().workoutPinnedNote)
        assertEquals("catalog", rendered.lifts.single().liftPinnedNote)
    }
}

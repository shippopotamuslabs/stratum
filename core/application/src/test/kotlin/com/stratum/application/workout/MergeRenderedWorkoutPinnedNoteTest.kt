package com.stratum.application.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection

class MergeRenderedWorkoutPinnedNoteTest {

    @Test
    @DisplayName("overlay workout pins win as a full list; structure catalog pins copy through")
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
                            workoutPinnedNotes = listOf("template"),
                            liftPinnedNotes = listOf("catalog", "second cue"),
                        ),
                    ),
            )
        val overlay =
            WorkoutOverlay(
                workoutLiftPinnedNoteLists = mapOf(workoutLiftId to listOf("overlay", "overlay two")),
            )
        val rendered =
            MergeRenderedWorkoutUseCase()(
                structure,
                ProgressionSnapshot(WorkoutId("w-1"), emptyMap()),
                overlay,
            )
        assertEquals(listOf("overlay", "overlay two"), rendered.lifts.single().workoutPinnedNotes)
        assertEquals(listOf("catalog", "second cue"), rendered.lifts.single().liftPinnedNotes)
    }

    @Test
    @DisplayName("template lists render fully when the overlay has no entry")
    fun templateListsRenderFully() {
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
                            workoutPinnedNotes = listOf("one", "two"),
                            liftPinnedNotes = listOf("catalog"),
                        ),
                    ),
            )
        val rendered =
            MergeRenderedWorkoutUseCase()(
                structure,
                ProgressionSnapshot(WorkoutId("w-1"), emptyMap()),
                WorkoutOverlay(),
            )
        assertEquals(listOf("one", "two"), rendered.lifts.single().workoutPinnedNotes)
        assertEquals(listOf("catalog"), rendered.lifts.single().liftPinnedNotes)
    }

    @Test
    @DisplayName("legacy single overlay pin renders as one row")
    fun legacySingleOverlayPinRenders() {
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
                        ),
                    ),
            )
        val rendered =
            MergeRenderedWorkoutUseCase()(
                structure,
                ProgressionSnapshot(WorkoutId("w-1"), emptyMap()),
                WorkoutOverlay(
                    workoutLiftPinnedNotes = mapOf(workoutLiftId to "legacy"),
                ),
            )
        assertEquals(listOf("legacy"), rendered.lifts.single().workoutPinnedNotes)
    }
}

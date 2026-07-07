package com.stratum.domain.training

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant

enum class ProgramChangeEventType {
    LIFT_SWAPPED,
    WORKOUT_EDITED,
    PROGRAM_REGENERATED,
    CYCLE_COMPLETED,
    SCHEME_CHANGED,
}

data class ProgramChangeEvent(
    val id: ProgramChangeEventId,
    val programId: ProgramId,
    val eventType: ProgramChangeEventType,
    val occurredAt: Instant,
    val payload: ProgramChangeEventPayload,
)

sealed interface ProgramChangeEventPayload {
    data class LiftSwapped(
        val workoutLiftId: WorkoutLiftId,
        val previousLiftId: LiftId,
        val newLiftId: LiftId,
    ) : ProgramChangeEventPayload

    data class WorkoutEdited(val workoutId: WorkoutId) : ProgramChangeEventPayload

    data object ProgramRegenerated : ProgramChangeEventPayload

    data class CycleCompleted(val completedCycleNumber: Int) : ProgramChangeEventPayload

    /** NEW (Slice 8): a progression-scheme change on a [WorkoutLift]. Append-only. */
    data class SchemeChanged(
        val workoutLiftId: WorkoutLiftId,
        val liftId: LiftId,
        val previous: ProgressionScheme,
        val current: ProgressionScheme,
    ) : ProgramChangeEventPayload
}

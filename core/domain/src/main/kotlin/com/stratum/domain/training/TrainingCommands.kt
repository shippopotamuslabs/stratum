package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant

sealed class StartTrainingSessionRequest {
    data class ProgramWorkout(val workoutId: WorkoutId) : StartTrainingSessionRequest()

    data object AdHocEmpty : StartTrainingSessionRequest()
}

data class LogSetCommand(
    val sessionId: TrainingSessionId,
    val liftId: LiftId,
    val workoutLiftId: WorkoutLiftId?,
    val setIndex: Int,
    val weight: Weight,
    val reps: Int,
    val rir: Int?,
    val completedAt: Instant,
    /**
     * Equipment active on the lift card at log time. Null resolves to the lift's
     * current equipment in [LogSetUseCase].
     */
    val equipmentId: EquipmentId? = null,
    /**
     * Live overlay slot fields. Always preferred for [LoggedLiftContext] when non-null so
     * auto-generated myo minis / retyped rows stamp correctly even when [workoutLiftId]
     * resolves to a template lift (setIndex may not match a template row after renumber).
     * When null, template lookup by setIndex is used as a fallback.
     */
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
    /** Incomplete extra reps after [reps]; null/0 means none logged. */
    val partialReps: Int? = null,
)

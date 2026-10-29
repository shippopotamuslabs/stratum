package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import com.stratum.domain.lift.Equipment

sealed class StartTrainingSessionRequest {
    data class ProgramWorkout(val workoutId: WorkoutId) : StartTrainingSessionRequest()

    data object AdHocEmpty : StartTrainingSessionRequest()
}

/**
 * Source of the equipment stamped on a set logged through [LogSetCommand].
 *
 * Null is deliberately absent from this contract: on the command, "resolve from the
 * lift" ([FromLift]) and "explicitly chosen" ([Explicit]) are distinct states, while
 * null on [LoggedSet.equipmentId] means legacy/unknown. Keeping the two meanings in
 * separate types stops future edits from conflating "unspecified, resolve me" with
 * "known absent".
 */
sealed interface SetEquipmentSource {
    /**
     * Resolve the lift's current equipment at log time. [LogSetUseCase] is the
     * single canonical resolver.
     */
    data object FromLift : SetEquipmentSource

    /**
     * Equipment explicitly active on the lift card at log time; wins over the
     * lift's current equipment.
     */
    data class Explicit(val equipmentId: EquipmentId) : SetEquipmentSource
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
     * Equipment the set was performed with. Defaults to [SetEquipmentSource.FromLift],
     * resolving to the lift's current equipment in [LogSetUseCase].
     */
    val equipment: SetEquipmentSource = SetEquipmentSource.FromLift,
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

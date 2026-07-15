package com.stratum.domain.training

import com.stratum.domain.lift.Weight
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
     * Live overlay slot fields. Used when [workoutLiftId] is not on the program template
     * (mid-session add / ad-hoc empty workout) so [LoggedLiftContext] still snapshots
     * set shape. Ignored when the template lift resolves successfully.
     */
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
)

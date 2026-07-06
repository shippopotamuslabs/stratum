package com.stratum.domain.training

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
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
)

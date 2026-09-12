package com.stratum.domain.training

import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId

sealed interface StartTrainingSessionResult {
    data class Success(val session: TrainingSession) : StartTrainingSessionResult
    data class ActiveSessionExists(val existingSessionId: TrainingSessionId) : StartTrainingSessionResult
    data object NoActiveProgram : StartTrainingSessionResult
    data class WorkoutNotFound(val workoutId: WorkoutId) : StartTrainingSessionResult
    data class Failure(val cause: Throwable) : StartTrainingSessionResult
}

val StartTrainingSessionResult.isSuccess: Boolean
    get() = this is StartTrainingSessionResult.Success

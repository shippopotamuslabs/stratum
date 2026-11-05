package com.stratum.domain.training

import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.identity.TrainingSessionId

sealed interface StartTrainingSessionResult {
    data class Success(val session: TrainingSession) : StartTrainingSessionResult
    data class ActiveSessionExists(val existingSessionId: TrainingSessionId) : StartTrainingSessionResult
    data object NoActiveProgram : StartTrainingSessionResult
    data class WorkoutNotFound(val workoutId: WorkoutId) : StartTrainingSessionResult
    /**
     * The session could not be started because a repository read or write failed.
     * The originating throwable is logged once at the use-case boundary through the
     * diagnostics channel (LoggingPort); it never crosses as a payload (STR-243).
     * Coroutine cancellation is not this branch: it propagates as cancellation.
     */
    data object PersistenceFailed : StartTrainingSessionResult
}

val StartTrainingSessionResult.isSuccess: Boolean
    get() = this is StartTrainingSessionResult.Success

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
     * The originating throwable rides this branch as a
     * [TrainingSessionFailureDiagnostic] and is recorded exactly once at the
     * presentation boundary (STR-237 Route (i), the domain-owned-outcome shape
     * of STR-243's diagnostics channel). Coroutine cancellation is not this
     * branch: it propagates as cancellation.
     */
    data class PersistenceFailed(
        val diagnostics: List<TrainingSessionFailureDiagnostic>,
    ) : StartTrainingSessionResult
}

val StartTrainingSessionResult.isSuccess: Boolean
    get() = this is StartTrainingSessionResult.Success

package com.stratum.application.training

import com.stratum.domain.coroutines.runCatchingCancellable
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import com.stratum.domain.training.LoggedSet

/**
 * Updates an existing in-progress session logged set (weight / reps / RIR) in place.
 * Used when the lifter amends a completed set without unlogging it.
 */
class UpdateLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(set: LoggedSet): Result<Unit> {
        // Single load via the seam check: a missing / non-live session and an operational
        // load failure are expected failures, returned as constructed values (never thrown)
        // and logged with the same line the envelope below produces. Cancellation from the
        // load propagates as cancellation out of the check.
        when (val check = trainingLogRepository.checkInProgressSession(set.sessionId)) {
            is InProgressSessionCheck.Rejected -> {
                loggingPort.error(TAG, "Failed to update logged set: ${check.error.message}", check.error)
                return Result.failure(check.error)
            }
            is InProgressSessionCheck.Ready -> Unit
        }
        return runCatchingCancellable {
            trainingLogRepository.updateSet(set)
        }.onFailure { error ->
            loggingPort.error(TAG, "Failed to update logged set: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "UpdateLoggedSetUseCase"
    }
}

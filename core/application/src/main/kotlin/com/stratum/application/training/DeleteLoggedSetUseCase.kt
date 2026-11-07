package com.stratum.application.training

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of deleting a logged set (STR-243). The originating throwable of a
 * failed delete goes once to the diagnostics channel ([LoggingPort.error]) at
 * the use-case boundary; it never crosses as a payload. Coroutine cancellation
 * is not a branch: it propagates.
 */
sealed interface DeleteLoggedSetResult {
    data object Success : DeleteLoggedSetResult

    /** The set delete failed (repository / store I/O). */
    data object PersistenceFailed : DeleteLoggedSetResult
}

class DeleteLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LoggedSetId): DeleteLoggedSetResult =
        try {
            trainingLogRepository.deleteSet(id)
            DeleteLoggedSetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to delete logged set: ${t.message}", t)
            DeleteLoggedSetResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "DeleteLoggedSetUseCase"
    }
}

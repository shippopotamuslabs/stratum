package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of abandoning a training session (STR-243). The originating throwable of
 * a failed delete goes once to the diagnostics channel ([LoggingPort.error]) at
 * the use-case boundary; it never crosses as a payload. Coroutine cancellation is
 * not a branch: it propagates.
 */
sealed interface AbandonTrainingSessionResult {
    data object Success : AbandonTrainingSessionResult

    /** The session delete failed (repository / store I/O). */
    data object PersistenceFailed : AbandonTrainingSessionResult
}

class AbandonTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): AbandonTrainingSessionResult =
        try {
            trainingLogRepository.deleteSession(sessionId)
            AbandonTrainingSessionResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to abandon training session: ${t.message}", t)
            AbandonTrainingSessionResult.PersistenceFailed
        }

    private companion object {
        const val TAG = "AbandonTrainingSession"
    }
}

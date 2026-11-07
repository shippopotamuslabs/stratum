package com.stratum.application.training

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of abandoning a training session (STR-243; STR-237 Route (i)).
 *
 * The originating throwable of a failed delete rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface AbandonTrainingSessionResult {
    data object Success : AbandonTrainingSessionResult

    /** The session delete failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AbandonTrainingSessionResult
}

class AbandonTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): AbandonTrainingSessionResult =
        try {
            trainingLogRepository.deleteSession(sessionId)
            AbandonTrainingSessionResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            AbandonTrainingSessionResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to abandon training session: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "AbandonTrainingSession"
    }
}

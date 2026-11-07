package com.stratum.application.training

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.importing.identity.LoggedSetId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of deleting a logged set (STR-243; STR-237 Route (i)).
 *
 * The originating throwable of a failed delete rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); the presentation boundary drains it exactly once
 * through its module's DiagnosticDrain. Coroutine cancellation is not a
 * branch: it propagates.
 */
sealed interface DeleteLoggedSetResult {
    data object Success : DeleteLoggedSetResult

    /** The set delete failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DeleteLoggedSetResult
}

class DeleteLoggedSetUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(id: LoggedSetId): DeleteLoggedSetResult =
        try {
            trainingLogRepository.deleteSet(id)
            DeleteLoggedSetResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            DeleteLoggedSetResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to delete logged set: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }

    private companion object {
        const val TAG = "DeleteLoggedSetUseCase"
    }
}

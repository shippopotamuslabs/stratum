package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of deleting a lift (STR-243). A missing or already-tombstoned lift
 * is a no-op [Success], matching the port contract. The originating throwable
 * of a failed delete goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a
 * payload. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface DeleteLiftResult {
    data object Success : DeleteLiftResult

    /** The tombstone write failed (repository / store I/O). */
    data object PersistenceFailed : DeleteLiftResult
}

/**
 * Deletes a lift (STR-131): seed and custom alike, delete means delete — the
 * row is tombstoned via [DeleteLiftPort] in one transaction with the template
 * slot + stash-note sweep. Logged history is never rewritten.
 *
 * After the commit, the derived rebuild runs so no derived/catalog surface
 * keeps presenting the lift; a rebuild failure is logged, not fatal — the
 * delete itself already succeeded.
 */
class DeleteLiftUseCase @Inject constructor(
    private val deleteLiftPort: DeleteLiftPort,
    private val rebuildDerived: RebuildLocalDerivedTrainingDataUseCase,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(id: LiftId): DeleteLiftResult {
        val tombstoned =
            try {
                deleteLiftPort.deleteLift(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Failed to delete lift: ${t.message}", t)
                return DeleteLiftResult.PersistenceFailed
            }

        if (tombstoned) {
            try {
                rebuildDerived()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                loggingPort.error(TAG, "Derived rebuild after lift delete failed: ${t.message}", t)
            }
        }
        return DeleteLiftResult.Success
    }

    private companion object {
        const val TAG = "DeleteLift"
    }
}

package com.stratum.application.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of deleting a lift (STR-243; STR-237 Route (i)). A missing or
 * already-tombstoned lift is a no-op [Success], matching the port contract.
 * The originating throwable of a failed delete rides the [PersistenceFailed]
 * branch as an [OperationalDiagnostic] (legacy tag + rendered message, real
 * throwable as cause); a post-commit rebuild failure the delete tolerates
 * rides [Success]'s diagnostics the same way (the §10 success-payload
 * precedent, cf. [MergeLiftsOutcome]). The presentation boundary drains them
 * exactly once through its module's DiagnosticDrain. Coroutine cancellation
 * is not a branch: it propagates.
 */
sealed interface DeleteLiftResult {
    data class Success(
        val diagnostics: List<OperationalDiagnostic> = emptyList(),
    ) : DeleteLiftResult

    /** The tombstone write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : DeleteLiftResult
}

/**
 * Deletes a lift (STR-131): seed and custom alike, delete means delete — the
 * row is tombstoned via [DeleteLiftPort] in one transaction with the template
 * slot + stash-note sweep. Logged history is never rewritten.
 *
 * After the commit, the derived rebuild runs so no derived/catalog surface
 * keeps presenting the lift; a rebuild failure is carried on [DeleteLiftResult.Success],
 * not fatal — the delete itself already succeeded. (A rebuild that returns
 * its failure branch instead of throwing was already recorded inside the
 * derived-training stages; only a thrown failure rides here.)
 */
class DeleteLiftUseCase @Inject constructor(
    private val deleteLiftPort: DeleteLiftPort,
    private val rebuildDerived: RebuildLocalDerivedTrainingDataUseCase,
) {
    suspend operator fun invoke(id: LiftId): DeleteLiftResult {
        val tombstoned =
            try {
                deleteLiftPort.deleteLift(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                return DeleteLiftResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to delete lift: ${t.message}",
                            cause = t,
                        ),
                    ),
                )
            }

        var rebuildDiagnostics: List<OperationalDiagnostic> = emptyList()
        if (tombstoned) {
            try {
                rebuildDerived()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                rebuildDiagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Derived rebuild after lift delete failed: ${t.message}",
                        cause = t,
                    ),
                )
            }
        }
        return DeleteLiftResult.Success(diagnostics = rebuildDiagnostics)
    }

    private companion object {
        const val TAG = "DeleteLift"
    }
}

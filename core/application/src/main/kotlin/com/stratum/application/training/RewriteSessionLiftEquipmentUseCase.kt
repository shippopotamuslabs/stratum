package com.stratum.application.training

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of rewriting session lift equipment (STR-243).
 *
 * The session-guard branches carry the seam's typed facts ([SessionNotFound] /
 * [SessionNotInProgress]). The originating throwable of a failed write rides
 * the [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary drains
 * it exactly once through its module's DiagnosticDrain. The guard branches'
 * former error breadcrumbs are dropped: their facts are the branches
 * themselves (STR-237). Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface RewriteSessionLiftEquipmentResult {
    data object Success : RewriteSessionLiftEquipmentResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : RewriteSessionLiftEquipmentResult

    data class SessionNotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : RewriteSessionLiftEquipmentResult

    /** The equipment rewrite failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : RewriteSessionLiftEquipmentResult
}

/**
 * Rewrites `equipmentId` on every set logged for [liftId] in [sessionId] — the
 * "rewrite to new equipment" path of the STR-122 partial-swap prompt.
 */
class RewriteSessionLiftEquipmentUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
) {
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        liftId: LiftId,
        newEquipmentId: EquipmentId,
    ): RewriteSessionLiftEquipmentResult {
        // Single load via the seam check: a missing / non-live session is returned as a
        // typed branch (never thrown); an operational load failure is captured by the
        // seam callback as a diagnostic with the same legacy line the write envelope
        // below produces and returned on PersistenceFailed.
        // Cancellation from the load propagates as cancellation out of the check.
        var loadDiagnostic: OperationalDiagnostic? = null
        when (val check = trainingLogRepository.checkInProgressSession(sessionId) { error ->
            loadDiagnostic =
                OperationalDiagnostic(
                    tag = TAG,
                    message = "Failed to rewrite session lift equipment: ${error.message}",
                    cause = error,
                )
        }) {
            is InProgressSessionCheck.NotFound ->
                return RewriteSessionLiftEquipmentResult.SessionNotFound(check.sessionId)
            is InProgressSessionCheck.NotInProgress ->
                return RewriteSessionLiftEquipmentResult.SessionNotInProgress(
                    check.sessionId,
                    check.status,
                )
            InProgressSessionCheck.LoadFailed ->
                return RewriteSessionLiftEquipmentResult.PersistenceFailed(
                    diagnostics = listOf(checkNotNull(loadDiagnostic)),
                )
            is InProgressSessionCheck.Ready -> Unit
        }
        return try {
            val matching =
                trainingLogRepository.getSetsForSession(sessionId)
                    .filter { it.liftId == liftId && it.equipmentId != newEquipmentId }
            if (matching.isNotEmpty()) {
                trainingLogRepository.updateSets(matching.map { it.copy(equipmentId = newEquipmentId) })
            }
            RewriteSessionLiftEquipmentResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            RewriteSessionLiftEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to rewrite session lift equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "RewriteSessionLiftEquipmentUseCase"
    }
}

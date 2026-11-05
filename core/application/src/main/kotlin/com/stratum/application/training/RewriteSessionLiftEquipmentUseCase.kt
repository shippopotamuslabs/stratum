package com.stratum.application.training

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionNotInProgressException
import com.stratum.domain.training.TrainingSessionStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Outcome of rewriting session lift equipment (STR-243).
 *
 * The session-guard branches carry the seam's typed facts ([SessionNotFound] /
 * [SessionNotInProgress]). The originating throwable of a failed write goes once
 * to the diagnostics channel ([LoggingPort.error]) at the use-case boundary; it
 * never crosses as a payload. Coroutine cancellation is not a branch: it
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
    data object PersistenceFailed : RewriteSessionLiftEquipmentResult
}

/**
 * Rewrites `equipmentId` on every set logged for [liftId] in [sessionId] — the
 * "rewrite to new equipment" path of the STR-122 partial-swap prompt.
 */
class RewriteSessionLiftEquipmentUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        liftId: LiftId,
        newEquipmentId: EquipmentId,
    ): RewriteSessionLiftEquipmentResult {
        // Single load via the seam check: a missing / non-live session is returned as a
        // typed branch (never thrown) and logged once with the same line the write
        // envelope below produces; an operational load failure is logged once by the
        // seam callback with that same line and returned as PersistenceFailed.
        // Cancellation from the load propagates as cancellation out of the check.
        when (val check = trainingLogRepository.checkInProgressSession(sessionId) { error ->
            loggingPort.error(
                TAG,
                "Failed to rewrite session lift equipment: ${error.message}",
                error,
            )
        }) {
            is InProgressSessionCheck.NotFound -> {
                val error =
                    IllegalArgumentException("Training session ${check.sessionId.value} not found")
                loggingPort.error(
                    TAG,
                    "Failed to rewrite session lift equipment: ${error.message}",
                    error,
                )
                return RewriteSessionLiftEquipmentResult.SessionNotFound(check.sessionId)
            }
            is InProgressSessionCheck.NotInProgress -> {
                val error = TrainingSessionNotInProgressException(check.sessionId, check.status)
                loggingPort.error(
                    TAG,
                    "Failed to rewrite session lift equipment: ${error.message}",
                    error,
                )
                return RewriteSessionLiftEquipmentResult.SessionNotInProgress(
                    check.sessionId,
                    check.status,
                )
            }
            InProgressSessionCheck.LoadFailed ->
                return RewriteSessionLiftEquipmentResult.PersistenceFailed
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
            loggingPort.error(TAG, "Failed to rewrite session lift equipment: ${t.message}", t)
            RewriteSessionLiftEquipmentResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "RewriteSessionLiftEquipmentUseCase"
    }
}

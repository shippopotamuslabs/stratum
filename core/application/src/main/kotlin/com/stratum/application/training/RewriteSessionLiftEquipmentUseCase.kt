package com.stratum.application.training

import com.stratum.domain.coroutines.runCatchingCancellable
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

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
    ): Result<Unit> {
        // Single load via the seam check: a missing / non-live session and an operational
        // load failure are expected failures, returned as constructed values (never thrown)
        // and logged with the same line the envelope below produces. Cancellation from the
        // load propagates as cancellation out of the check.
        when (val check = trainingLogRepository.checkInProgressSession(sessionId)) {
            is InProgressSessionCheck.Rejected -> {
                loggingPort.error(
                    TAG,
                    "Failed to rewrite session lift equipment: ${check.error.message}",
                    check.error,
                )
                return Result.failure(check.error)
            }
            is InProgressSessionCheck.Ready -> Unit
        }
        return runCatchingCancellable {
            val matching =
                trainingLogRepository.getSetsForSession(sessionId)
                    .filter { it.liftId == liftId && it.equipmentId != newEquipmentId }
            if (matching.isNotEmpty()) {
                trainingLogRepository.updateSets(matching.map { it.copy(equipmentId = newEquipmentId) })
            }
        }.onFailure { error ->
            loggingPort.error(TAG, "Failed to rewrite session lift equipment: ${error.message}", error)
        }
    }

    private companion object {
        const val TAG = "RewriteSessionLiftEquipmentUseCase"
    }
}

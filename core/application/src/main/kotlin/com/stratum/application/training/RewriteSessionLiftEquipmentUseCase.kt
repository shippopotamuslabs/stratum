package com.stratum.application.training

import com.stratum.domain.coroutines.runCatchingCancellable
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.TrainingSessionNotInProgressException
import com.stratum.domain.training.TrainingSessionStatus
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
        // Pre-check status after load: a non-live session is an expected failure, returned
        // as a constructed value (never thrown). The helper call below stays as the backstop.
        val loadedSession = trainingLogRepository.getSession(sessionId)
        if (loadedSession != null && loadedSession.status != TrainingSessionStatus.IN_PROGRESS) {
            val error = TrainingSessionNotInProgressException(loadedSession.id, loadedSession.status)
            loggingPort.error(TAG, "Failed to rewrite session lift equipment: ${error.message}", error)
            return Result.failure(error)
        }
        return runCatchingCancellable {
            trainingLogRepository.requireInProgressSession(sessionId)
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

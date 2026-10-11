package com.stratum.domain.training

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
    ): Result<Unit> = runCatching {
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

    private companion object {
        const val TAG = "RewriteSessionLiftEquipmentUseCase"
    }
}

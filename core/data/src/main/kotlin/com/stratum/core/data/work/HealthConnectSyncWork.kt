package com.stratum.core.data.work

import com.stratum.domain.healthconnect.HealthConnectPreferencesPort
import com.stratum.domain.healthconnect.HealthConnectSyncPort
import com.stratum.domain.healthconnect.HealthConnectSyncStatusPort
import com.stratum.domain.healthconnect.isTerminalHealthConnectFailure
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.identity.TrainingSessionId
import javax.inject.Inject

/**
 * Pure orchestration for Health Connect session sync. Extracted from [HealthConnectSyncWorker]
 * so unit tests can exercise prefs gating and terminal vs retryable failures without WorkManager.
 */
class HealthConnectSyncWork @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val preferencesPort: HealthConnectPreferencesPort,
    private val healthConnectSyncPort: HealthConnectSyncPort,
    private val healthConnectSyncStatusPort: HealthConnectSyncStatusPort,
) {
    sealed interface Outcome {
        /** User disabled sync after enqueue — do not write. */
        data object SkippedDisabled : Outcome

        data object Success : Outcome

        /** Permanent failure; report once and do not retry. */
        data class TerminalFailure(val message: String) : Outcome

        /** Transient failure; WorkManager may retry without emitting yet. */
        data class RetryableFailure(val message: String) : Outcome
    }

    suspend fun run(sessionId: TrainingSessionId): Outcome {
        if (!preferencesPort.getPreferences().enabled) {
            return Outcome.SkippedDisabled
        }

        val session = trainingLogRepository.getSession(sessionId)
            ?: return Outcome.TerminalFailure("Training session ${sessionId.value} not found")

        val sets = trainingLogRepository.getSetsForSession(sessionId)
        val syncResult = healthConnectSyncPort.pushSession(session, sets)
        return syncResult.fold(
            onSuccess = { Outcome.Success },
            onFailure = { error ->
                val message = error.message ?: "Failed to sync workout to Health Connect"
                if (isTerminalHealthConnectFailure(error)) {
                    Outcome.TerminalFailure(message)
                } else {
                    Outcome.RetryableFailure(message)
                }
            },
        )
    }

    suspend fun reportTerminalFailure(message: String) {
        healthConnectSyncStatusPort.reportSyncError(message)
    }
}

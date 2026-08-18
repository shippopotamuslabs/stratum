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
        /** User disabled sync, or write was not granted — do not write and do not error. */
        data object SkippedDisabled : Outcome

        data object Success : Outcome

        /** Permanent failure; report once and do not retry. */
        data class TerminalFailure(val error: Throwable) : Outcome

        /** Transient failure; WorkManager may retry without emitting yet. */
        data class RetryableFailure(val error: Throwable) : Outcome
    }

    suspend fun run(sessionId: TrainingSessionId): Outcome {
        if (!preferencesPort.getPreferences().enabled) {
            return Outcome.SkippedDisabled
        }
        if (!healthConnectSyncPort.hasWritePermission()) {
            return Outcome.SkippedDisabled
        }

        val session = trainingLogRepository.getSession(sessionId)
            ?: return Outcome.TerminalFailure(
                IllegalStateException("Training session ${sessionId.value} not found"),
            )

        val sets = trainingLogRepository.getSetsForSession(sessionId)
        val syncResult = healthConnectSyncPort.pushSession(session, sets)
        return syncResult.fold(
            onSuccess = { Outcome.Success },
            onFailure = { error ->
                if (isTerminalHealthConnectFailure(error)) {
                    Outcome.TerminalFailure(error)
                } else {
                    Outcome.RetryableFailure(error)
                }
            },
        )
    }

    suspend fun reportTerminalFailure(message: String) {
        healthConnectSyncStatusPort.reportSyncError(message)
    }
}

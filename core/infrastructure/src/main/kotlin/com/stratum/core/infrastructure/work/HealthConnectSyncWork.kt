package com.stratum.core.infrastructure.work

import com.stratum.application.healthconnect.HealthConnectPushOutcome
import com.stratum.application.healthconnect.HealthConnectPreferencesPort
import com.stratum.application.healthconnect.HealthConnectSyncPort
import com.stratum.application.healthconnect.HealthConnectSyncStatusPort
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.application.healthconnect.isTerminal
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.application.training.TrainingLogRepository
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

        /**
         * Permanent failure; report once and do not retry. [kind] is the port
         * failure's classification, or null for a work-local failure (missing
         * session) that has no classification. [cause] is the originating
         * throwable for the worker's single log record.
         */
        data class TerminalFailure(
            val kind: HealthSyncFailureKind?,
            val message: String?,
            val cause: Throwable?,
        ) : Outcome

        /** Transient failure; WorkManager may retry without emitting yet. */
        data class RetryableFailure(
            val kind: HealthSyncFailureKind?,
            val message: String?,
            val cause: Throwable?,
        ) : Outcome
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
                kind = null,
                message = "Training session ${sessionId.value} not found",
                cause = IllegalStateException("Training session ${sessionId.value} not found"),
            )

        val sets = trainingLogRepository.getSetsForSession(sessionId)
        return when (val outcome = healthConnectSyncPort.pushSession(session, sets)) {
            HealthConnectPushOutcome.Success -> Outcome.Success
            is HealthConnectPushOutcome.Failed -> {
                val cause = outcome.diagnostics
                    .filterIsInstance<HealthSyncDiagnostic.OperationFailed>()
                    .firstOrNull()?.cause
                if (outcome.kind.isTerminal()) {
                    Outcome.TerminalFailure(outcome.kind, outcome.message, cause)
                } else {
                    Outcome.RetryableFailure(outcome.kind, outcome.message, cause)
                }
            }
        }
    }

    suspend fun reportTerminalFailure(kind: HealthFailureKind): HealthConnectWriteOutcome =
        healthConnectSyncStatusPort.reportSyncError(kind)
}

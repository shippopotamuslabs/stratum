package com.stratum.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stratum.application.healthconnect.HEALTH_CONNECT_PREFS_TAG
import com.stratum.application.healthconnect.HealthConnectWriteOutcome
import com.stratum.application.healthconnect.HealthSyncDiagnostic
import com.stratum.application.healthconnect.HealthSyncFailureKind
import com.stratum.application.healthconnect.toHealthFailureKind
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val work: HealthConnectSyncWork,
    private val loggingPort: LoggingPort,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionIdValue = inputData.getString(WorkManagerHealthConnectScheduler.KEY_SESSION_ID)
            ?: return Result.failure()

        val outcome = work.run(TrainingSessionId(sessionIdValue))
        return when (outcome) {
            is HealthConnectSyncWork.Outcome.SkippedDisabled,
            is HealthConnectSyncWork.Outcome.Success,
            -> Result.success()

            is HealthConnectSyncWork.Outcome.TerminalFailure -> {
                loggingPort.error(
                    TAG,
                    "Health Connect sync terminal failure: ${outcome.message}",
                    outcome.cause,
                )
                reportStickySyncError(outcome.kind.toStickyHealthFailureKind())
                Result.failure()
            }

            is HealthConnectSyncWork.Outcome.RetryableFailure -> {
                val result =
                    retryOrGiveUp(
                        runAttemptCount = runAttemptCount,
                        maxRetries = 2,
                        tag = TAG,
                        taskDescription = "Health Connect sync",
                        error = outcome.cause,
                        loggingPort = loggingPort,
                    )
                if (result == Result.failure()) {
                    reportStickySyncError(outcome.kind.toStickyHealthFailureKind())
                }
                result
            }
        }
    }

    /**
     * Persists the sticky sync error for UI surfacing. The work layer returns
     * the write failure as an outcome without logging it; this worker is the
     * drain boundary, so each failure diagnostic is logged exactly once here.
     */
    private suspend fun reportStickySyncError(kind: HealthFailureKind) {
        val outcome = work.reportTerminalFailure(kind)
        if (outcome is HealthConnectWriteOutcome.Failed) {
            outcome.diagnostics.forEach { diagnostic ->
                when (diagnostic) {
                    is HealthSyncDiagnostic.OperationFailed ->
                        loggingPort.error(
                            HEALTH_CONNECT_PREFS_TAG,
                            "Failed to set Health Connect sync error: ${diagnostic.cause.message}",
                            diagnostic.cause,
                        )
                }
            }
        }
    }

    private companion object {
        const val TAG = "HealthConnectSyncWorker"
    }
}

/**
 * Derives the sticky kind from a sync failure's classification (D8): a
 * classified failure maps by kind; an unclassified (work-local) failure
 * persists as [HealthFailureKind.Unknown].
 */
internal fun HealthSyncFailureKind?.toStickyHealthFailureKind(): HealthFailureKind =
    this?.toHealthFailureKind() ?: HealthFailureKind.Unknown

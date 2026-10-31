package com.stratum.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stratum.application.healthconnect.HEALTH_CONNECT_PREFS_TAG
import com.stratum.application.healthconnect.HealthSyncFailure
import com.stratum.application.healthconnect.toHealthFailureKind
import com.stratum.domain.healthconnect.HealthFailureKind
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.onFailureLogged
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
                    "Health Connect sync terminal failure: ${outcome.error.message}",
                    outcome.error,
                )
                reportStickySyncError(outcome.error.toHealthFailureKind())
                Result.failure()
            }

            is HealthConnectSyncWork.Outcome.RetryableFailure -> {
                val result =
                    retryOrGiveUp(
                        runAttemptCount = runAttemptCount,
                        maxRetries = 2,
                        tag = TAG,
                        taskDescription = "Health Connect sync",
                        error = outcome.error,
                        loggingPort = loggingPort,
                    )
                if (result == Result.failure()) {
                    reportStickySyncError(outcome.error.toHealthFailureKind())
                }
                result
            }
        }
    }

    /**
     * Persists the sticky sync error for UI surfacing. The work layer returns
     * the write failure as a value without logging it; this worker is the
     * drain boundary, so each failure is logged exactly once here.
     */
    private suspend fun reportStickySyncError(kind: HealthFailureKind) {
        work.reportTerminalFailure(kind).onFailureLogged(
            loggingPort,
            HEALTH_CONNECT_PREFS_TAG,
            "Failed to set Health Connect sync error",
        )
    }

    private companion object {
        const val TAG = "HealthConnectSyncWorker"
    }
}

/**
 * Derives the sticky kind from the outcome error (D8): classified application
 * failures map by kind; unclassified throwables persist as [HealthFailureKind.Unknown].
 */
internal fun Throwable.toHealthFailureKind(): HealthFailureKind =
    (this as? HealthSyncFailure)?.kind?.toHealthFailureKind() ?: HealthFailureKind.Unknown

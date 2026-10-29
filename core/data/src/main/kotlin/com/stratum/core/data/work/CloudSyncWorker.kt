package com.stratum.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.stratum.core.data.sync.CloudSyncFeatureGate
import com.stratum.core.data.sync.CloudSyncResidualException
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.sync.CloudSyncPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Network-constrained background push/pull. When sync is not started (signed out),
 * completes as success no-op so WorkManager does not retry forever.
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncPort: CloudSyncPort,
    private val featureGate: CloudSyncFeatureGate,
    private val loggingPort: LoggingPort,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!featureGate.isEnabled()) return Result.success()
        val outcome = cloudSyncPort.requestSync()
        return mapCloudSyncWorkResult(
            outcome = outcome,
            runAttemptCount = runAttemptCount,
            loggingPort = loggingPort,
        )
    }
}

internal const val CLOUD_SYNC_WORKER_TAG = "CloudSyncWorker"

/**
 * Residual backlog keeps retrying with WorkManager backoff. Other failures use the bounded
 * [retryOrGiveUp] policy. UI/scheduler retries must enqueue outside [CloudSyncWorker.doWork].
 */
internal fun mapCloudSyncWorkResult(
    outcome: kotlin.Result<Unit>,
    runAttemptCount: Int,
    loggingPort: LoggingPort,
): ListenableWorker.Result {
    if (outcome.isSuccess) return ListenableWorker.Result.success()
    return when (val error = outcome.exceptionOrNull()) {
        is CloudSyncNotStartedException -> ListenableWorker.Result.success()
        is CloudSyncResidualException -> ListenableWorker.Result.retry()
        else ->
            retryOrGiveUp(
                runAttemptCount = runAttemptCount,
                tag = CLOUD_SYNC_WORKER_TAG,
                taskDescription = "Cloud sync",
                error = error,
                loggingPort = loggingPort,
            )
    }
}

/** Thrown by [CloudSyncPort.requestSync] when no active session; worker treats as no-op success. */
class CloudSyncNotStartedException : IllegalStateException("Cloud sync not started")


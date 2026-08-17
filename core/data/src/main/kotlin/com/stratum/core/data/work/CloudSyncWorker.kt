package com.stratum.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stratum.core.data.sync.CloudSyncFeatureGate
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.sync.CloudSyncPort
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
        return when {
            outcome.isSuccess -> Result.success()
            // Permanent: not signed in / not started — do not retry.
            outcome.exceptionOrNull() is CloudSyncNotStartedException -> Result.success()
            else -> {
                val error = outcome.exceptionOrNull()
                retryOrGiveUp(
                    runAttemptCount = runAttemptCount,
                    tag = TAG,
                    taskDescription = "Cloud sync",
                    error = error,
                    loggingPort = loggingPort,
                )
            }
        }
    }

    private companion object {
        const val TAG = "CloudSyncWorker"
    }
}

/** Thrown by [CloudSyncPort.requestSync] when no active session; worker treats as no-op success. */
class CloudSyncNotStartedException : IllegalStateException("Cloud sync not started")


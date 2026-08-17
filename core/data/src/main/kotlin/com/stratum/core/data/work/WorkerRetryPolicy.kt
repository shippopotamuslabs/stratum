package com.stratum.core.data.work

import androidx.work.ListenableWorker.Result
import com.stratum.domain.logging.LoggingPort

internal const val DEFAULT_WORKER_MAX_RETRIES = 3

/**
 * Common bounded-retry policy for CoroutineWorkers.
 * Logs a warning on intermediate retry attempts, and logs an error with the throwable when
 * retries are exhausted before returning [Result.failure].
 */
internal fun retryOrGiveUp(
    runAttemptCount: Int,
    maxRetries: Int = DEFAULT_WORKER_MAX_RETRIES,
    tag: String,
    taskDescription: String,
    error: Throwable?,
    loggingPort: LoggingPort,
): Result {
    return if (runAttemptCount < maxRetries) {
        loggingPort.warning(
            tag,
            "$taskDescription attempt $runAttemptCount failed: ${error?.message}",
        )
        Result.retry()
    } else {
        loggingPort.error(
            tag,
            "$taskDescription exceeded max retries ($maxRetries): ${error?.message}",
            error,
        )
        Result.failure()
    }
}

package com.stratum.core.data.work

import androidx.work.ListenableWorker.Result
import com.stratum.domain.logging.LoggingPort

internal const val DEFAULT_WORKER_MAX_RETRIES = 3

/**
 * Common bounded-retry policy for CoroutineWorkers.
 * Logs a warning on intermediate retry attempts, and logs an error with the throwable when
 * retries are exhausted before returning [Result.failure].
 *
 * @param onTerminalError when present and retries are exhausted, records the
 * terminal failure through it INSTEAD of the give-up [LoggingPort.error], so
 * the exceptional fact has exactly one record (STR-242).
 */
internal fun retryOrGiveUp(
    runAttemptCount: Int,
    maxRetries: Int = DEFAULT_WORKER_MAX_RETRIES,
    tag: String,
    taskDescription: String,
    error: Throwable?,
    loggingPort: LoggingPort,
    onTerminalError: ((Throwable) -> Unit)? = null,
): Result {
    return if (runAttemptCount < maxRetries) {
        loggingPort.warning(
            tag,
            "$taskDescription attempt $runAttemptCount failed: ${error?.message}",
        )
        Result.retry()
    } else if (error != null && onTerminalError != null) {
        onTerminalError(error)
        Result.failure()
    } else {
        loggingPort.error(
            tag,
            "$taskDescription exceeded max retries ($maxRetries): ${error?.message}",
            error,
        )
        Result.failure()
    }
}

package com.stratum.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stratum.domain.logging.LoggingPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager entry for the full dose-response / outcome-index pipeline.
 *
 * Enqueued non-blockingly from [WorkManagerResponseModelingScheduler] after workout finalize.
 * The 28-day re-extract gate and extract call live in [ResponseModelingWork] so unit tests do not
 * need a live WorkManager.
 *
 * Contract: lives in `:core:data` (ARCHITECTURE.md §6). Deterministic only — no network/LLM.
 */
@HiltWorker
class ResponseModelingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val work: ResponseModelingWork,
    private val loggingPort: LoggingPort,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val forceHistoryInvalidation =
            inputData.getBoolean(
                WorkManagerResponseModelingScheduler.KEY_FORCE_HISTORY_INVALIDATION,
                false,
            )
        return when (val outcome = work.run(forceHistoryInvalidation = forceHistoryInvalidation)) {
            is ResponseModelingWork.Outcome.Skipped,
            is ResponseModelingWork.Outcome.Success,
            -> Result.success()

            is ResponseModelingWork.Outcome.Failed -> {
                retryOrGiveUp(
                    runAttemptCount = runAttemptCount,
                    tag = TAG,
                    taskDescription = "Response modeling extraction",
                    error = outcome.error,
                    loggingPort = loggingPort,
                )
            }
        }
    }

    private companion object {
        const val TAG = "ResponseModelingWorker"
    }
}


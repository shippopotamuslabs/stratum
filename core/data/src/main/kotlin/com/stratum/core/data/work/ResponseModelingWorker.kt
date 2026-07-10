package com.stratum.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val forceHistoryInvalidation =
            inputData.getBoolean(
                WorkManagerResponseModelingScheduler.KEY_FORCE_HISTORY_INVALIDATION,
                false,
            )
        return when (work.run(forceHistoryInvalidation = forceHistoryInvalidation)) {
            is ResponseModelingWork.Outcome.Skipped,
            is ResponseModelingWork.Outcome.Success,
            -> Result.success()

            is ResponseModelingWork.Outcome.Failed -> Result.retry()
        }
    }
}

package com.stratum.core.infrastructure.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.stratum.application.response.ResponseModelingSchedulerPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `:core:data` implementation of [ResponseModelingSchedulerPort]. Enqueues a unique one-time
 * [ResponseModelingWorker] without blocking the caller (WorkManager schedules only).
 *
 * Why design (A) — port + enqueue from finalize — not a pure periodic app-start job:
 * - New training data is the natural trigger; import already runs a full extract once.
 * - Domain finalize stays free of WorkManager types (port in domain, impl here).
 * - [ExistingWorkPolicy.KEEP] coalesces rapid finalize storms into one pending job; after the job
 *   finishes the worker's workout-count gate no-ops until due again.
 * - Force extract ([enqueueForceExtract]) uses [KEY_FORCE_HISTORY_INVALIDATION] so the worker
 *   skips the workout-count gate. History edits pass `replaceExisting = true` (REPLACE);
 *   Settings recovery passes `false` (KEEP) so re-taps do not cancel an in-flight job.
 */
@Singleton
class WorkManagerResponseModelingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ResponseModelingSchedulerPort {

    override fun enqueueRefresh() {
        enqueue(forceHistoryInvalidation = false, policy = ExistingWorkPolicy.KEEP)
    }

    override fun enqueueForceExtract(replaceExisting: Boolean) {
        enqueue(
            forceHistoryInvalidation = true,
            policy =
                if (replaceExisting) {
                    ExistingWorkPolicy.REPLACE
                } else {
                    ExistingWorkPolicy.KEEP
                },
        )
    }

    private fun enqueue(
        forceHistoryInvalidation: Boolean,
        policy: ExistingWorkPolicy,
    ) {
        val request =
            OneTimeWorkRequestBuilder<ResponseModelingWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInputData(
                    workDataOf(KEY_FORCE_HISTORY_INVALIDATION to forceHistoryInvalidation),
                )
                .addTag(WORK_TAG)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "response_modeling_extract"
        const val WORK_TAG = "response_modeling"
        const val KEY_FORCE_HISTORY_INVALIDATION = "force_history_invalidation"
    }
}

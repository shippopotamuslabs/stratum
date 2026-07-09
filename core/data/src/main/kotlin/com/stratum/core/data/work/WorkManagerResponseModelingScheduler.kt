package com.stratum.core.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.stratum.domain.response.ResponseModelingSchedulerPort
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
 *   finishes the worker's 28-day gate no-ops until due again.
 */
@Singleton
class WorkManagerResponseModelingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ResponseModelingSchedulerPort {

    override fun enqueueRefresh() {
        val request = OneTimeWorkRequestBuilder<ResponseModelingWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "response_modeling_extract"
        const val WORK_TAG = "response_modeling"
    }
}

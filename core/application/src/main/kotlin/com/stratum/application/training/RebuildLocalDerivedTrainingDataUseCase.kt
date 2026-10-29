package com.stratum.application.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.application.response.ResponseModelingSchedulerPort
import javax.inject.Inject
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.application.dashboard.LoadDashboardUseCase
import com.stratum.application.sync.CloudSyncPort

/**
 * Rebuilds device-local derived training tables that are intentionally **not** cloud-synced
 * (see [com.stratum.application.sync.CloudSyncPort] incomplete surface).
 *
 * After multi-device hydrate, raw [LoggedSet] / [TrainingSession] rows land in Room (Gym history
 * works) but `muscle_weekly_stimulus` / `lift_weekly_performance` stay empty until recomputed —
 * the dashboard charts read only those tables via [com.stratum.application.dashboard.LoadDashboardUseCase].
 *
 * Stages:
 *  1. [LocalDerivedTrainingStages] weekly series only (insights already live on the source device).
 *  2. [ResponseModelingSchedulerPort.enqueueRefresh] — heavy outcome-index / progress line
 *     (WorkManager; may lag the volume charts; applies the 20-workout gate).
 *
 * Invoked after a successful cloud push/pull so a new device (or incremental remote training
 * edits) never leaves the dashboard empty while lift history is populated.
 *
 * @see ForceRefreshDerivedTrainingDataUseCase recovery path (insights + weekly + force extract)
 */
class RebuildLocalDerivedTrainingDataUseCase @Inject constructor(
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val responseModelingScheduler: ResponseModelingSchedulerPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<Unit> {
        val stagesResult =
            localDerivedStages.run(
                includeInsights = false,
                failPolicy = LocalDerivedTrainingStages.FailPolicy.Throw,
                logTag = TAG,
            )
        if (stagesResult.isFailure) {
            // Stage already logged; keep a top-level breadcrumb for hydrate callers.
            loggingPort.error(
                TAG,
                "Weekly series rebuild failed: ${stagesResult.exceptionOrNull()?.message}",
                stagesResult.exceptionOrNull(),
            )
            return stagesResult
        }
        // Non-blocking enqueue — worker owns gates / extract cost.
        responseModelingScheduler.enqueueRefresh()
        return Result.success(Unit)
    }

    private companion object {
        const val TAG = "RebuildLocalDerived"
    }
}

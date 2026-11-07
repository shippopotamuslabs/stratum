package com.stratum.application.training

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.response.ResponseModelingSchedulerPort
import javax.inject.Inject
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.application.dashboard.LoadDashboardUseCase
import com.stratum.application.sync.CloudSyncPort

/**
 * Outcome of rebuilding local derived training data (STR-243; STR-237
 * Route (i)). The stage that failed is identified on
 * [LocalDerivedTrainingStagesResult]; its failure records ride
 * [PersistenceFailed.diagnostics] unchanged from the stages result, and the
 * boundary that owns this result drains them exactly once. Coroutine
 * cancellation is not a branch: it propagates out of the stages.
 */
sealed interface RebuildLocalDerivedTrainingDataResult {
    data object Success : RebuildLocalDerivedTrainingDataResult

    /** A derived-data stage failed (see [LocalDerivedTrainingStagesResult]). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : RebuildLocalDerivedTrainingDataResult
}

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
) {
    suspend operator fun invoke(): RebuildLocalDerivedTrainingDataResult {
        val stagesResult =
            localDerivedStages.run(
                includeInsights = false,
                failPolicy = LocalDerivedTrainingStages.FailPolicy.Throw,
                logTag = TAG,
            )
        if (stagesResult !is LocalDerivedTrainingStagesResult.Success) {
            // The stage failure records ride the stages result; propagate
            // them unchanged on this outcome's branch for the boundary to
            // drain (STR-237: exactly one record per failure, no duplicate
            // breadcrumb).
            val diagnostics =
                when (stagesResult) {
                    is LocalDerivedTrainingStagesResult.InsightsFailed -> stagesResult.diagnostics
                    is LocalDerivedTrainingStagesResult.WeeklySeriesFailed -> stagesResult.diagnostics
                    is LocalDerivedTrainingStagesResult.PersistenceFailed -> stagesResult.diagnostics
                    is LocalDerivedTrainingStagesResult.Success -> emptyList()
                }
            return RebuildLocalDerivedTrainingDataResult.PersistenceFailed(diagnostics)
        }
        // Non-blocking enqueue — worker owns gates / extract cost.
        responseModelingScheduler.enqueueRefresh()
        return RebuildLocalDerivedTrainingDataResult.Success
    }

    private companion object {
        const val TAG = "RebuildLocalDerived"
    }
}

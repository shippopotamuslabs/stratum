package com.stratum.domain.training

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.response.ResponseModelingSchedulerPort
import javax.inject.Inject

/**
 * Rebuilds device-local derived training tables that are intentionally **not** cloud-synced
 * (see [com.stratum.domain.sync.CloudSyncPort] incomplete surface).
 *
 * After multi-device hydrate, raw [LoggedSet] / [TrainingSession] rows land in Room (Gym history
 * works) but `muscle_weekly_stimulus` / `lift_weekly_performance` stay empty until recomputed —
 * the dashboard charts read only those tables via [com.stratum.domain.dashboard.LoadDashboardUseCase].
 *
 * Stages (independently fail-safe):
 *  1. [RefreshWeeklySeriesUseCase] — cheap volume + peak-e1RM series the board requires.
 *  2. [ResponseModelingSchedulerPort.enqueueRefresh] — heavy outcome-index / progress line
 *     (WorkManager; may lag the volume charts).
 *
 * Invoked after a successful cloud push/pull so a new device (or incremental remote training
 * edits) never leaves the dashboard empty while lift history is populated.
 */
class RebuildLocalDerivedTrainingDataUseCase @Inject constructor(
    private val refreshWeeklySeries: RefreshWeeklySeriesUseCase,
    private val responseModelingScheduler: ResponseModelingSchedulerPort,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val weeklyResult = refreshWeeklySeries()
        if (weeklyResult.isFailure) {
            loggingPort.error(
                TAG,
                "Weekly series rebuild failed: ${weeklyResult.exceptionOrNull()?.message}",
                weeklyResult.exceptionOrNull(),
            )
            weeklyResult.getOrThrow()
        }
        // Non-blocking enqueue — worker owns gates / extract cost.
        responseModelingScheduler.enqueueRefresh()
    }

    private companion object {
        const val TAG = "RebuildLocalDerived"
    }
}

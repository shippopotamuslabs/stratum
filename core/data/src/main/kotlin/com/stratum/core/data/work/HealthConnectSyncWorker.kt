package com.stratum.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.identity.TrainingSessionId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val work: HealthConnectSyncWork,
    private val loggingPort: LoggingPort,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionIdValue = inputData.getString(WorkManagerHealthConnectScheduler.KEY_SESSION_ID)
            ?: return Result.failure()

        val outcome = work.run(TrainingSessionId(sessionIdValue))
        return when (outcome) {
            is HealthConnectSyncWork.Outcome.SkippedDisabled,
            is HealthConnectSyncWork.Outcome.Success,
            -> Result.success()

            is HealthConnectSyncWork.Outcome.TerminalFailure -> {
                val error = RuntimeException(outcome.message)
                loggingPort.error(
                    TAG,
                    "Health Connect sync terminal failure: ${outcome.message}",
                    error,
                )
                work.reportTerminalFailure(outcome.message)
                Result.failure()
            }

            is HealthConnectSyncWork.Outcome.RetryableFailure -> {
                val result =
                    retryOrGiveUp(
                        runAttemptCount = runAttemptCount,
                        maxRetries = 2,
                        tag = TAG,
                        taskDescription = "Health Connect sync",
                        error = RuntimeException(outcome.message),
                        loggingPort = loggingPort,
                    )
                if (result == Result.failure()) {
                    work.reportTerminalFailure(outcome.message)
                }
                result
            }
        }
    }

    private companion object {
        const val TAG = "HealthConnectSyncWorker"
    }
}

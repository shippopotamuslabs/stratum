package com.stratum.core.infrastructure.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.stratum.core.infrastructure.sync.CloudSyncFeatureGate
import com.stratum.core.infrastructure.sync.CloudSyncResidualException
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.OutcomeResult
import com.stratum.domain.telemetry.OutcomeTelemetry
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.REASON_SYNC_NOT_STARTED
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.REASON_SYNC_RESIDUAL_BACKLOG
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.USE_CASE_CLOUD_SYNC
import com.stratum.domain.telemetry.TelemetryRouter
import com.stratum.application.sync.CloudSyncPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Network-constrained background push/pull. When sync is not started (signed out),
 * completes as success no-op so WorkManager does not retry forever.
 */
@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudSyncPort: CloudSyncPort,
    private val featureGate: CloudSyncFeatureGate,
    private val loggingPort: LoggingPort,
    private val telemetryRouter: TelemetryRouter,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!featureGate.isEnabled()) return Result.success()
        val outcome = cloudSyncPort.requestSync()
        // STR-242: terminal boundary for the sync flow. Classified outcomes go
        // to the countable stream; an unexpected failure that exhausted retries
        // goes through the router exactly once (non-fatal + bare counter).
        classifySyncOutcome(outcome)?.let(telemetryRouter::emitOutcome)
        return mapCloudSyncWorkResult(
            outcome = outcome,
            runAttemptCount = runAttemptCount,
            loggingPort = loggingPort,
            onTerminalExceptional = { error ->
                telemetryRouter.recordExceptional(CLOUD_SYNC_WORKER_TAG, error)
            },
        )
    }
}

/**
 * Single discriminant for a sync [Result] (STR-242): the one place that maps
 * the exception hierarchy to a class. Both the countable-outcome classifier
 * ([classifySyncOutcome]) and the WorkManager mapping
 * ([mapCloudSyncWorkResult]) consume this, so adding or re-homing a failure
 * class is a one-branch edit that cannot drift between the two paths.
 */
internal sealed interface SyncWorkClassification {
    data object Success : SyncWorkClassification

    data object NotStarted : SyncWorkClassification

    data object ResidualBacklog : SyncWorkClassification

    data class Unexpected(val error: Throwable?) : SyncWorkClassification
}

internal fun classifySyncWork(outcome: kotlin.Result<Unit>): SyncWorkClassification =
    when {
        outcome.isSuccess -> SyncWorkClassification.Success
        outcome.exceptionOrNull() is CloudSyncNotStartedException -> SyncWorkClassification.NotStarted
        outcome.exceptionOrNull() is CloudSyncResidualException -> SyncWorkClassification.ResidualBacklog
        else -> SyncWorkClassification.Unexpected(outcome.exceptionOrNull())
    }

/**
 * Maps a sync [Result] to its countable outcome fact, or null when the failure
 * is exceptional (Crashlytics home, not the outcome stream).
 */
internal fun classifySyncOutcome(outcome: kotlin.Result<Unit>): OutcomeTelemetry? =
    when (classifySyncWork(outcome)) {
        SyncWorkClassification.Success ->
            OutcomeTelemetry(
                useCase = USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.Success,
            )
        SyncWorkClassification.NotStarted ->
            OutcomeTelemetry(
                useCase = USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(REASON_SYNC_NOT_STARTED),
            )
        SyncWorkClassification.ResidualBacklog ->
            OutcomeTelemetry(
                useCase = USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(REASON_SYNC_RESIDUAL_BACKLOG),
            )
        is SyncWorkClassification.Unexpected -> null
    }

internal const val CLOUD_SYNC_WORKER_TAG = "CloudSyncWorker"

/**
 * Residual backlog keeps retrying with WorkManager backoff. Other failures use the bounded
 * [retryOrGiveUp] policy. UI/scheduler retries must enqueue outside [CloudSyncWorker.doWork].
 *
 * @param onTerminalExceptional records an unexpected failure that exhausted retries.
 * When present, it replaces the give-up [LoggingPort.error] for that fact so the
 * exceptional record exists exactly once (STR-242); when null, the legacy error
 * log applies.
 */
internal fun mapCloudSyncWorkResult(
    outcome: kotlin.Result<Unit>,
    runAttemptCount: Int,
    loggingPort: LoggingPort,
    onTerminalExceptional: ((Throwable) -> Unit)? = null,
): ListenableWorker.Result =
    when (val classification = classifySyncWork(outcome)) {
        SyncWorkClassification.Success,
        SyncWorkClassification.NotStarted,
        -> ListenableWorker.Result.success()
        SyncWorkClassification.ResidualBacklog -> ListenableWorker.Result.retry()
        is SyncWorkClassification.Unexpected ->
            retryOrGiveUp(
                runAttemptCount = runAttemptCount,
                tag = CLOUD_SYNC_WORKER_TAG,
                taskDescription = "Cloud sync",
                error = classification.error,
                loggingPort = loggingPort,
                onTerminalError = onTerminalExceptional,
            )
    }

/** Thrown by [CloudSyncPort.requestSync] when no active session; worker treats as no-op success. */
class CloudSyncNotStartedException : IllegalStateException("Cloud sync not started")


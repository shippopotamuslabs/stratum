package com.stratum.core.inference

import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.TelemetryUploadPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [TelemetryUploadPort] via the `ingestClientLogs` callable (STR-27 tier 2).
 *
 * Own failures are local-logcat only ([AndroidLoggingAdapter.debug]): routing
 * them back through [com.stratum.domain.logging.LoggingPort] would re-enqueue
 * them into the buffer being flushed — an infinite feedback loop.
 */
@Singleton
class FirebaseTelemetryUploadAdapter @Inject constructor(
    private val functions: FirebaseFunctions,
    private val localLog: AndroidLoggingAdapter,
) : TelemetryUploadPort {
    override suspend fun upload(batch: ClientLogBatch): Result<Unit> {
        if (batch.entries.isEmpty()) {
            return Result.success(Unit)
        }
        return try {
            functions
                .getHttpsCallable(INGEST_CALLABLE)
                .call(TelemetryPayloadMapper.buildPayload(batch))
                .await()
            Result.success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            localLog.debug(TAG, "ingestClientLogs upload failed: ${error.message}")
            Result.failure(error)
        }
    }

    private companion object {
        const val TAG = "TelemetryUpload"
        const val INGEST_CALLABLE = "ingestClientLogs"
    }
}

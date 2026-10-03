package com.stratum.core.inference

import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.TelemetryUploadPort
import javax.inject.Inject
import javax.inject.Singleton
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
    private val localLog: AndroidLoggingAdapter,
) : TelemetryUploadPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    override suspend fun upload(batch: ClientLogBatch): Result<Unit> {
        if (batch.entries.isEmpty()) {
            return Result.success(Unit)
        }
        return try {
            functions
                .getHttpsCallable(INGEST_CALLABLE)
                .call(buildIngestPayload(batch))
                .await()
            Result.success(Unit)
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

/** Callable wire payload for [ClientLogBatch]; kept pure for JVM tests. */
internal fun buildIngestPayload(batch: ClientLogBatch): Map<String, Any?> =
    mapOf(
        PAYLOAD_PLATFORM to batch.platform,
        PAYLOAD_APP_VERSION to batch.appVersion,
        PAYLOAD_DROPPED_COUNT to batch.droppedEntryCount,
        PAYLOAD_ENTRIES to batch.entries.map(::buildIngestEntry),
    )

private fun buildIngestEntry(entry: ClientLogEntry): Map<String, Any?> =
    buildMap {
        put(PAYLOAD_SEVERITY, entry.severity.name)
        put(PAYLOAD_TAG, entry.tag)
        put(PAYLOAD_MESSAGE, entry.message)
        put(PAYLOAD_CLIENT_TIMESTAMP_MS, entry.clientTimestampMs)
        entry.sessionId?.let { put(PAYLOAD_SESSION_ID, it) }
        entry.durationMs?.let { put(PAYLOAD_DURATION_MS, it) }
    }

private const val PAYLOAD_PLATFORM = "platform"
private const val PAYLOAD_APP_VERSION = "appVersion"
private const val PAYLOAD_DROPPED_COUNT = "droppedEntryCount"
private const val PAYLOAD_ENTRIES = "entries"
private const val PAYLOAD_SEVERITY = "severity"
private const val PAYLOAD_TAG = "tag"
private const val PAYLOAD_MESSAGE = "message"
private const val PAYLOAD_CLIENT_TIMESTAMP_MS = "clientTimestampMs"
private const val PAYLOAD_SESSION_ID = "sessionId"
private const val PAYLOAD_DURATION_MS = "durationMs"

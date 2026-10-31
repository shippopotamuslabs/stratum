package com.stratum.core.infrastructure.inference

import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.ClientLogEntry

/**
 * Pure builder for the `ingestClientLogs` callable wire payload (STR-27 tier 2).
 *
 * Split out of [FirebaseTelemetryUploadAdapter] so the mapping stays
 * synchronously testable while the adapter owns only the network call and its
 * [Result] mapping.
 */
internal object TelemetryPayloadMapper {
    fun buildPayload(batch: ClientLogBatch): Map<String, Any?> =
        mapOf(
            PAYLOAD_PLATFORM to batch.platform,
            PAYLOAD_APP_VERSION to batch.appVersion,
            PAYLOAD_DROPPED_COUNT to batch.droppedEntryCount,
            PAYLOAD_ENTRIES to batch.entries.map(::buildEntry),
        )

    fun buildEntry(entry: ClientLogEntry): Map<String, Any?> =
        buildMap {
            put(PAYLOAD_SEVERITY, entry.severity.name)
            put(PAYLOAD_TAG, entry.tag)
            put(PAYLOAD_MESSAGE, entry.message)
            put(PAYLOAD_CLIENT_TIMESTAMP_MS, entry.clientTimestampMs)
            entry.sessionId?.let { put(PAYLOAD_SESSION_ID, it) }
            entry.durationMs?.let { put(PAYLOAD_DURATION_MS, it) }
            entry.outcome?.let { put(PAYLOAD_OUTCOME, it) }
            entry.reasonCode?.let { put(PAYLOAD_REASON_CODE, it) }
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
    private const val PAYLOAD_OUTCOME = "outcome"
    private const val PAYLOAD_REASON_CODE = "reasonCode"
}

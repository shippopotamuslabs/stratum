package com.stratum.core.infrastructure.telemetry

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.ClientLogSeverity
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore mirror of [ClientLogEntry] for the tier-2 offline queue (STR-27).
 * Severity travels as a string so an unknown future value fails closed (entry
 * dropped on load) instead of crashing deserialization of the whole queue.
 */
@Serializable
data class PendingClientLogEntryRecord(
    val severity: String = "",
    val tag: String = "",
    val message: String = "",
    val clientTimestampMs: Long = 0L,
    val sessionId: String? = null,
    val durationMs: Long? = null,
    val outcome: String? = null,
    val reasonCode: String? = null,
)

@Serializable
data class PendingClientLogRecord(
    val entries: List<PendingClientLogEntryRecord> = emptyList(),
)

private val pendingClientLogJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

object PendingClientLogRecordSerializer : Serializer<PendingClientLogRecord> {
    override val defaultValue: PendingClientLogRecord = PendingClientLogRecord()

    override suspend fun readFrom(input: InputStream): PendingClientLogRecord {
        val text = input.bufferedReader().use { it.readText() }
        if (text.isBlank()) return defaultValue
        return runCatching {
            pendingClientLogJson.decodeFromString(PendingClientLogRecord.serializer(), text)
        }.getOrDefault(defaultValue)
    }

    override suspend fun writeTo(t: PendingClientLogRecord, output: OutputStream) {
        val text = pendingClientLogJson.encodeToString(PendingClientLogRecord.serializer(), t)
        output.bufferedWriter().use { it.write(text) }
    }
}

internal val Context.pendingClientLogDataStore by dataStore(
    fileName = "pending_client_logs.json",
    serializer = PendingClientLogRecordSerializer,
)

internal fun ClientLogEntry.toRecord() =
    PendingClientLogEntryRecord(
        severity = severity.name,
        tag = tag,
        message = message,
        clientTimestampMs = clientTimestampMs,
        sessionId = sessionId,
        durationMs = durationMs,
        outcome = outcome,
        reasonCode = reasonCode,
    )

internal fun PendingClientLogEntryRecord.toDomain(): ClientLogEntry? {
    if (tag.isBlank() || message.isBlank()) {
        return null
    }
    val parsedSeverity =
        runCatching { ClientLogSeverity.valueOf(severity) }.getOrNull() ?: return null
    return ClientLogEntry(
        severity = parsedSeverity,
        tag = tag,
        message = message,
        clientTimestampMs = clientTimestampMs,
        sessionId = sessionId,
        durationMs = durationMs,
        outcome = outcome,
        reasonCode = reasonCode,
    )
}

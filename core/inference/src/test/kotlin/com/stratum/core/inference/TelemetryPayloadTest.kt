package com.stratum.core.inference

import com.stratum.domain.telemetry.ClientLogBatch
import com.stratum.domain.telemetry.ClientLogEntry
import com.stratum.domain.telemetry.ClientLogSeverity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TelemetryPayloadTest {
    private fun entry(
        message: String = "warn",
        sessionId: String? = "run-1",
        durationMs: Long? = null,
    ) = ClientLogEntry(
        severity = ClientLogSeverity.WARNING,
        tag = "Tag",
        message = message,
        clientTimestampMs = 1_000L,
        sessionId = sessionId,
        durationMs = durationMs,
    )

    @Test
    @DisplayName("payload carries envelope, severity names, and optional fields")
    fun payloadShape() {
        val batch =
            ClientLogBatch(
                entries = listOf(entry("one", durationMs = 9L), entry("two", sessionId = null)),
                droppedEntryCount = 2,
                appVersion = "1.2.3",
            )

        val payload = buildIngestPayload(batch)

        assertEquals("android", payload["platform"])
        assertEquals("1.2.3", payload["appVersion"])
        assertEquals(2, payload["droppedEntryCount"])
        @Suppress("UNCHECKED_CAST")
        val entries = payload["entries"] as List<Map<String, Any?>>
        assertEquals(2, entries.size)
        assertEquals("WARNING", entries[0]["severity"])
        assertEquals("Tag", entries[0]["tag"])
        assertEquals("run-1", entries[0]["sessionId"])
        assertEquals(9L, entries[0]["durationMs"])
        assertFalse(entries[1].containsKey("sessionId"))
        assertFalse(entries[1].containsKey("durationMs"))
    }

    @Test
    @DisplayName("resolveAppVersion falls back for null or blank versions")
    fun versionFallback() {
        assertEquals("1.2.3", resolveAppVersion("1.2.3"))
        assertEquals(ClientLogBatch.APP_VERSION_UNKNOWN, resolveAppVersion(null))
        assertEquals(ClientLogBatch.APP_VERSION_UNKNOWN, resolveAppVersion("  "))
    }
}

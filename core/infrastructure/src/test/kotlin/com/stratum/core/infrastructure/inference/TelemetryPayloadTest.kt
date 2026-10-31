package com.stratum.core.infrastructure.inference

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

        val payload = TelemetryPayloadMapper.buildPayload(batch)

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
    @DisplayName("outcome entries carry flat outcome columns; legacy entries omit them")
    fun outcomeColumns() {
        val outcomeEntry =
            ClientLogEntry(
                severity = ClientLogSeverity.OUTCOME,
                tag = "program_generation",
                message = "outcome=expected_failure reason=synthesis_failed",
                clientTimestampMs = 2_000L,
                sessionId = "run-2",
                durationMs = 77L,
                outcome = "expected_failure",
                reasonCode = "synthesis_failed",
            )

        val mapped = TelemetryPayloadMapper.buildEntry(outcomeEntry)

        assertEquals("OUTCOME", mapped["severity"])
        assertEquals("expected_failure", mapped["outcome"])
        assertEquals("synthesis_failed", mapped["reasonCode"])
        assertEquals(77L, mapped["durationMs"])

        val legacy = TelemetryPayloadMapper.buildEntry(entry("warn"))
        assertFalse(legacy.containsKey("outcome"))
        assertFalse(legacy.containsKey("reasonCode"))
    }

}

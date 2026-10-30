package com.stratum.domain.telemetry

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ClientLogEntry")
class ClientLogEntryTest {
    private fun entry(
        severity: ClientLogSeverity,
        outcome: String? = null,
        reasonCode: String? = null,
    ) = ClientLogEntry(
        severity = severity,
        tag = "program_generation",
        message = "outcome=success",
        clientTimestampMs = 1_000L,
        sessionId = "run-1",
        outcome = outcome,
        reasonCode = reasonCode,
    )

    @Test
    @DisplayName("OUTCOME requires the outcome class")
    fun outcomeRequiresOutcomeColumn() {
        assertThrows<IllegalArgumentException> {
            entry(severity = ClientLogSeverity.OUTCOME)
        }
    }

    @Test
    @DisplayName("WARNING and ERROR reject outcome columns")
    fun legacySeveritiesRejectOutcomeColumns() {
        assertThrows<IllegalArgumentException> {
            entry(severity = ClientLogSeverity.WARNING, outcome = "success")
        }
        assertThrows<IllegalArgumentException> {
            entry(severity = ClientLogSeverity.ERROR, reasonCode = "sync_not_started")
        }
    }

    @Test
    @DisplayName("OUTCOME with outcome class and legacy entries without columns are valid")
    fun validCombinationsPass() {
        entry(severity = ClientLogSeverity.OUTCOME, outcome = "success")
        entry(
            severity = ClientLogSeverity.OUTCOME,
            outcome = "expected_failure",
            reasonCode = "synthesis_failed",
        )
        entry(severity = ClientLogSeverity.WARNING)
    }
}

package com.stratum.core.infrastructure.inference

import com.stratum.core.testing.telemetry.FakePendingClientLogStore
import com.stratum.core.testing.telemetry.FakeTelemetryUploadPort
import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.ClientLogSeverity
import com.stratum.domain.telemetry.OutcomeResult
import com.stratum.domain.telemetry.OutcomeTelemetry
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.REASON_SYNC_NOT_STARTED
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.USE_CASE_CLOUD_SYNC
import com.stratum.domain.telemetry.OutcomeTelemetry.Companion.USE_CASE_PROGRAM_GENERATION
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClientLogOutcomeSinkTest {
    @Test
    fun `success outcome records OUTCOME entry with flat outcome column`() = runTest {
        val upload = FakeTelemetryUploadPort()
        val buffer =
            ClientLogBuffer(
                scope = this,
                upload = upload,
                pendingStore = FakePendingClientLogStore(),
                appVersion = "1.2.3",
            )
        val sink = ClientLogOutcomeSink(buffer)

        sink.record(
            OutcomeTelemetry(
                useCase = USE_CASE_PROGRAM_GENERATION,
                result = OutcomeResult.Success,
                durationMs = 50L,
            ),
        )
        buffer.flush()

        val entry = upload.uploadedBatches.single().entries.single()
        assertEquals(ClientLogSeverity.OUTCOME, entry.severity)
        assertEquals(USE_CASE_PROGRAM_GENERATION, entry.tag)
        assertEquals("outcome=success", entry.message)
        assertEquals("success", entry.outcome)
        assertNull(entry.reasonCode)
        assertEquals(50L, entry.durationMs)
        assertEquals(buffer.sessionId, entry.sessionId)
    }

    @Test
    fun `expected failure records reason column and message`() = runTest {
        val upload = FakeTelemetryUploadPort()
        val buffer =
            ClientLogBuffer(
                scope = this,
                upload = upload,
                pendingStore = FakePendingClientLogStore(),
                appVersion = "1.2.3",
            )
        val sink = ClientLogOutcomeSink(buffer)

        sink.record(
            OutcomeTelemetry(
                useCase = USE_CASE_CLOUD_SYNC,
                result = OutcomeResult.ExpectedFailure(REASON_SYNC_NOT_STARTED),
            ),
        )
        buffer.flush()

        val entry = upload.uploadedBatches.single().entries.single()
        assertEquals(ClientLogSeverity.OUTCOME, entry.severity)
        assertEquals(USE_CASE_CLOUD_SYNC, entry.tag)
        assertEquals("outcome=expected_failure reason=$REASON_SYNC_NOT_STARTED", entry.message)
        assertEquals("expected_failure", entry.outcome)
        assertEquals(REASON_SYNC_NOT_STARTED, entry.reasonCode)
    }
}

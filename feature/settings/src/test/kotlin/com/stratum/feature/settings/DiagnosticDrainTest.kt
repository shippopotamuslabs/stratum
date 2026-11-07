package com.stratum.feature.settings

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.core.testing.logging.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticDrainTest {

    @Test
    fun errorDiagnosticRecordsExactlyOneError() {
        val loggingPort = FakeLoggingPort()
        val drain = DiagnosticDrain(loggingPort)
        val cause = IllegalStateException("boom")

        drain.drain(
            listOf(
                OperationalDiagnostic(
                    tag = "Tag",
                    message = "Failed.",
                    cause = cause,
                    level = OperationalDiagnostic.Level.ERROR,
                ),
            ),
        )

        val error = loggingPort.errors.single()
        assertEquals("Tag", error.tag)
        assertEquals("Failed.", error.message)
        assertSame(cause, error.throwable)
        assertTrue(loggingPort.warnings.isEmpty())
    }

    @Test
    fun warningDiagnosticRecordsExactlyOneWarning() {
        val loggingPort = FakeLoggingPort()
        val drain = DiagnosticDrain(loggingPort)
        val cause = IllegalStateException("boom")

        drain.drain(
            listOf(
                OperationalDiagnostic(
                    tag = "Tag",
                    message = "Degraded.",
                    cause = cause,
                    level = OperationalDiagnostic.Level.WARNING,
                ),
            ),
        )

        val warning = loggingPort.warnings.single()
        assertEquals("Tag", warning.tag)
        assertEquals("Degraded.", warning.message)
        assertTrue(loggingPort.errors.isEmpty())
    }

    @Test
    fun emptyListRecordsNothing() {
        val loggingPort = FakeLoggingPort()
        val drain = DiagnosticDrain(loggingPort)

        drain.drain(emptyList())

        assertTrue(loggingPort.errors.isEmpty())
        assertTrue(loggingPort.warnings.isEmpty())
    }

    @Test
    fun recordErrorRecordsExactlyOneErrorWithThrowable() {
        val loggingPort = FakeLoggingPort()
        val drain = DiagnosticDrain(loggingPort)
        val throwable = IllegalStateException("Disk full")

        drain.recordError("FileStorage", "Failed writing export", throwable)

        val entry = loggingPort.errors.single()
        assertEquals("FileStorage", entry.tag)
        assertEquals("Failed writing export", entry.message)
        assertSame(throwable, entry.throwable)
        assertTrue(loggingPort.warnings.isEmpty())
    }

    @Test
    fun recordErrorRecordsExactlyOneErrorWithoutThrowableWhenOmitted() {
        val loggingPort = FakeLoggingPort()
        val drain = DiagnosticDrain(loggingPort)

        drain.recordError("Sync", "Network timeout")

        val entry = loggingPort.errors.single()
        assertEquals("Sync", entry.tag)
        assertEquals("Network timeout", entry.message)
        assertNull(entry.throwable)
        assertTrue(loggingPort.warnings.isEmpty())
    }
}

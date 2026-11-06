package com.stratum.feature.workout

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.core.testing.logging.FakeLoggingPort
import org.junit.jupiter.api.Assertions.assertEquals
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
}

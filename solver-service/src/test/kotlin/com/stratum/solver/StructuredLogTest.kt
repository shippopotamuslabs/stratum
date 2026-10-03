package com.stratum.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * STR-28: server logs must carry severity + top-level uid/jobId correlation so
 * BigQuery / Logs Explorer can filter without regex, and only reportError may
 * carry the Error Reporting marker.
 */
class StructuredLogTest {
    private data class Line(val text: String, val toStderr: Boolean)

    private class FakeSink : LogSink {
        val lines = mutableListOf<Line>()
        override fun write(line: String, toStderr: Boolean) {
            lines += Line(line, toStderr)
        }
    }

    @Test
    fun `record keeps severity service message and correlation top-level`() {
        val line = StructuredLog.record(
            severity = LogSeverity.INFO,
            service = "stratum-solver",
            message = "job started",
            uid = "u1",
            jobId = "j1",
            durationMs = 42L,
        )
        assertTrue(line.startsWith("{"), "single JSON object, got: $line")
        assertTrue("\"severity\":\"INFO\"" in line, line)
        assertTrue("\"service\":\"stratum-solver\"" in line, line)
        assertTrue("\"message\":\"job started\"" in line, line)
        assertTrue("\"uid\":\"u1\"" in line, line)
        assertTrue("\"jobId\":\"j1\"" in line, line)
        assertTrue("\"durationMs\":42" in line, line)
        assertTrue("\"@type\"" !in line, line)
    }

    @Test
    fun `errorReport adds marker serviceContext and stack`() {
        val error = IllegalStateException("boom")
        val line = StructuredLog.errorReport(
            service = "stratum-solver",
            message = "job failed",
            error = error,
            uid = "u1",
            jobId = "j1",
        )
        assertTrue("\"@type\":\"${StructuredLog.ERROR_REPORT_TYPE}\"" in line, line)
        assertTrue("\"severity\":\"ERROR\"" in line, line)
        assertTrue("\"serviceContext\":{\"service\":\"stratum-solver\"}" in line, line)
        assertTrue("\"errorType\":\"java.lang.IllegalStateException\"" in line, line)
        assertTrue("\"errorMessage\":\"boom\"" in line, line)
        assertTrue("job failed: java.lang.IllegalStateException: boom" in line, line)
        assertTrue("StructuredLogTest" in line, "stack trace embedded, got: $line")
    }

    @Test
    fun `record encodes typed extras centrally`() {
        val line = StructuredLog.record(
            severity = LogSeverity.INFO,
            service = "stratum-solver",
            message = "solved",
            extra = listOf(
                "detail" to "a\"b",
                "port" to 8080,
                "bytes" to 12345678901L,
                "ok" to true,
                "missing" to null,
                "ratio" to 0.5,
            ),
        )
        // Strings are escaped, never raw: the old pre-encoded contract would
        // emit invalid JSON here if the caller forgot jsonString().
        assertTrue("\"detail\":\"a\\\"b\"" in line, line)
        // Numbers and booleans are unquoted; null is a literal.
        assertTrue("\"port\":8080" in line, line)
        assertTrue("\"bytes\":12345678901" in line, line)
        assertTrue("\"ok\":true" in line, line)
        assertTrue("\"missing\":null" in line, line)
        assertTrue("\"ratio\":0.5" in line, line)
    }

    @Test
    fun `errorReport accepts extras`() {
        val line = StructuredLog.errorReport(
            service = "stratum-solver",
            message = "job failed",
            error = IllegalStateException("boom"),
            extra = listOf("attempt" to 3),
        )
        assertTrue("\"attempt\":3" in line, line)
        assertTrue("\"@type\":\"${StructuredLog.ERROR_REPORT_TYPE}\"" in line, line)
    }

    @Test
    fun `logger routes info to stdout and warnings errors to stderr`() {
        val sink = FakeSink()
        val logger = StructuredLogger("stratum-solver", sink)
        logger.info("started", uid = "u1", jobId = "j1")
        logger.warn("degraded", uid = "u1", jobId = "j1")
        logger.error("failed without exception", uid = "u1", jobId = "j1")
        logger.reportError("threw", IllegalStateException("x"), uid = "u1", jobId = "j1")

        assertEquals(4, sink.lines.size)
        assertEquals(false, sink.lines[0].toStderr)
        assertEquals(true, sink.lines[1].toStderr)
        assertEquals(true, sink.lines[2].toStderr)
        assertEquals(true, sink.lines[3].toStderr)
        assertTrue("\"severity\":\"INFO\"" in sink.lines[0].text, sink.lines[0].text)
        assertTrue("\"severity\":\"WARNING\"" in sink.lines[1].text, sink.lines[1].text)
        assertTrue("\"severity\":\"ERROR\"" in sink.lines[2].text, sink.lines[2].text)
        assertTrue("\"@type\"" !in sink.lines[2].text, "plain error opens no issue")
        assertTrue(
            "\"@type\":\"${StructuredLog.ERROR_REPORT_TYPE}\"" in sink.lines[3].text,
            sink.lines[3].text,
        )
    }
}

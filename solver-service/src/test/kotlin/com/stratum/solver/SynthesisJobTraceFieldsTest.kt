package com.stratum.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * STR-13: enforces the cross-language trace contract against
 * `functions/src/program-synthesis/traceFields.json` — the single source of
 * truth for retry-reset fields. The Functions side derives its `TRACE_*`
 * constants and `staleTraceDeletions()` from that file; this test pins the
 * worker's `TRACE_*` constants against it, so a TS/Kotlin divergence fails
 * here instead of silently keeping stale worker timestamps on retried jobs.
 *
 * The schema file is resolved by walking up from the test working dir (the
 * module dir under Gradle) to the repo root — no network, no new
 * infrastructure; it runs in the existing `:solver-service:test` CI lane.
 */
class SynthesisJobTraceFieldsTest {
    @Test
    fun `worker trace fields match the shared traceFields schema`() {
        val schemaFields = readStaleTraceFields()
        assertEquals(
            schemaFields.size,
            schemaFields.toSet().size,
            "traceFields.json must not list a field twice",
        )
        val workerConstants =
            setOf(
                SynthesisJobWorker.TRACE_WORKER_START,
                SynthesisJobWorker.TRACE_WORKER_CLAIMED,
                SynthesisJobWorker.TRACE_WORKER_SOLVING,
                SynthesisJobWorker.TRACE_WORKER_UPLOADED,
                SynthesisJobWorker.TRACE_SOLVER,
            )
        // Every worker-written field must be cleared when a jobId is reused,
        // or a retried job shows the previous attempt's solve timestamps.
        for (constant in workerConstants) {
            assertTrue(
                schemaFields.contains(constant),
                "traceFields.json is missing worker field $constant",
            )
        }
        // The schema must not list worker/solver fields the worker never
        // writes: an unknown entry would delete nothing and hide the drift.
        val schemaWorkerFields =
            schemaFields
                .filter { it.startsWith("trace.worker") || it == SynthesisJobWorker.TRACE_SOLVER }
                .toSet()
        assertEquals(workerConstants, schemaWorkerFields)
    }

    private fun readStaleTraceFields(): List<String> {
        val text = findTraceFieldsSchema().readText()
        val keyIndex = text.indexOf("\"staleTraceFields\"")
        require(keyIndex >= 0) { "traceFields.json has no staleTraceFields array" }
        val openBracket = text.indexOf('[', keyIndex)
        val closeBracket = if (openBracket >= 0) text.indexOf(']', openBracket) else -1
        require(openBracket >= 0 && closeBracket > openBracket) {
            "traceFields.json staleTraceFields array is malformed"
        }
        val arrayBody = text.substring(openBracket + 1, closeBracket)
        val values =
            Regex("\"([^\"]+)\"").findAll(arrayBody).map { it.groupValues[1] }.toList()
        require(values.isNotEmpty()) { "traceFields.json staleTraceFields array is empty" }
        return values
    }

    private fun findTraceFieldsSchema(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(dir, "functions/src/program-synthesis/traceFields.json")
            if (candidate.isFile) {
                return candidate
            }
            dir = dir.parentFile ?: error(
                "traceFields.json not found walking up from ${System.getProperty("user.dir")}",
            )
        }
    }
}

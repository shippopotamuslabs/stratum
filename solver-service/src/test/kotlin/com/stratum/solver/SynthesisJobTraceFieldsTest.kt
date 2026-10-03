package com.stratum.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * STR-13: pins the per-job `trace` map field names the worker writes. These must
 * match the Functions-side reset list in `synthesisTrace.ts`
 * (`staleTraceDeletions`) or retried jobs keep stale worker timestamps.
 */
class SynthesisJobTraceFieldsTest {
    @Test
    fun `worker trace fields use trace map dot notation`() {
        assertEquals("trace.workerStart", SynthesisJobWorker.TRACE_WORKER_START)
        assertEquals("trace.workerClaimed", SynthesisJobWorker.TRACE_WORKER_CLAIMED)
        assertEquals("trace.workerSolving", SynthesisJobWorker.TRACE_WORKER_SOLVING)
        assertEquals("trace.workerUploaded", SynthesisJobWorker.TRACE_WORKER_UPLOADED)
        assertEquals("trace.solver", SynthesisJobWorker.TRACE_SOLVER)
    }
}

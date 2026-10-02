package com.stratum.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * STR-21: the Cloud Run worker must explicitly match CP-SAT parallelism to its `--cpu=8`
 * provisioning. Pins the composition-root default (the worker passes this constant into
 * `CpSatProgramSynthesizer`; the domain default stays generic).
 */
class SynthesisJobWorkerConfigTest {
    @Test
    fun `solver search workers match eight vCPUs`() {
        assertEquals(8, SynthesisJobWorker.SOLVER_SEARCH_WORKERS)
    }
}

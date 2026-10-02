@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * STR-21: the Cloud Run worker (stratum-solver, --cpu=8) must run CP-SAT with an explicit
 * search-worker count rather than relying on the OR-Tools default.
 * Solver construction is native-free, so these run without [com.google.ortools.Loader].
 */
class CpSatSolverThreadingTest {
    @Test
    fun `default solver uses eight search workers`() {
        val solver = CpSatProgramSynthesizer().buildSolver()

        assertEquals(8, solver.parameters.numSearchWorkers)
    }

    @Test
    fun `configured worker count is applied to the solver`() {
        val solver = CpSatProgramSynthesizer(numSearchWorkers = 2).buildSolver()

        assertEquals(2, solver.parameters.numSearchWorkers)
    }

    @Test
    fun `solver keeps the configured deadline alongside the worker count`() {
        val solver = CpSatProgramSynthesizer(deadlineMillis = 30_000L).buildSolver()

        assertEquals(30.0, solver.parameters.maxTimeInSeconds)
        assertEquals(8, solver.parameters.numSearchWorkers)
    }
}

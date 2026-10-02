@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * STR-21: worker parallelism must be explicit where the hardware is known, while the domain
 * default leaves the OR-Tools default untouched. [CpSatProgramSynthesizer.appliedParameters]
 * reads the values back from a freshly built solver, so these fail if the settings stop
 * being applied. Solver construction is native-free, so these run without
 * [com.google.ortools.Loader].
 */
class CpSatSolverThreadingTest {
    @Test
    fun `default leaves the OR-Tools worker default untouched`() {
        val parameters = CpSatProgramSynthesizer().appliedParameters()

        assertNull(parameters.numSearchWorkers)
    }

    @Test
    fun `explicit worker count is applied to the solver`() {
        val parameters = CpSatProgramSynthesizer(numSearchWorkers = 8).appliedParameters()

        assertEquals(8, parameters.numSearchWorkers)
    }

    @Test
    fun `custom worker count propagates instead of a hardcoded value`() {
        val parameters = CpSatProgramSynthesizer(numSearchWorkers = 2).appliedParameters()

        assertEquals(2, parameters.numSearchWorkers)
    }

    @Test
    fun `solver keeps the configured deadline alongside the worker count`() {
        val parameters =
            CpSatProgramSynthesizer(deadlineMillis = 30_000L, numSearchWorkers = 8)
                .appliedParameters()

        assertEquals(30.0, parameters.maxTimeSeconds)
        assertEquals(8, parameters.numSearchWorkers)
    }
}

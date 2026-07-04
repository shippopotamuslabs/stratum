@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Smoke test: confirms the OR-Tools native libraries load on the JVM test
 * runtime and the CP-SAT solver can solve a trivial model. If this fails, the
 * native loader cannot find `libortools.so` / `libjniortools.so` for the host
 * platform and the whole CP-SAT prototype is blocked.
 */
class OrToolsLoaderSmokeTest {

    @Test
    fun cpSatSolverSolvesTrivialModel() {
        Loader.loadNativeLibraries()
        val model = CpModel()
        val x = model.newIntVar(0, 10, "x")
        val y = model.newIntVar(0, 10, "y")
        model.addEquality(LinearExpr.sum(arrayOf(x, y)), 7)
        model.maximize(x)

        val solver = CpSolver()
        val status = solver.solve(model)

        assertEquals(CpSolverStatus.OPTIMAL, status)
        assertEquals(7, solver.value(x).toInt())
        assertEquals(0, solver.value(y).toInt())
    }
}
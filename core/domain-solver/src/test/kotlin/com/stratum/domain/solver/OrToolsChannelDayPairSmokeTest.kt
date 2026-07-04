@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.util.Domain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class OrToolsChannelDayPairSmokeTest {
  @Test
  fun channelDayPairIsTrueWhenDaysMatch() {
    val model = CpModel()
    val dayI = model.newIntVarFromDomain(Domain.fromValues(longArrayOf(0, 1)), "dayI")
    val dayJ = model.newIntVarFromDomain(Domain.fromValues(longArrayOf(0, 1)), "dayJ")
    val onI = model.newBoolVar("onI")
    model.addEquality(dayI, 0L).onlyEnforceIf(onI)
    model.addDifferent(dayI, 0L).onlyEnforceIf(onI.not())
    val onJ = model.newBoolVar("onJ")
    model.addEquality(dayJ, 1L).onlyEnforceIf(onJ)
    model.addDifferent(dayJ, 1L).onlyEnforceIf(onJ.not())
    val both = model.newBoolVar("both")
    model.addBoolAnd(arrayOf(onI, onJ)).onlyEnforceIf(both)
    model.addBoolOr(arrayOf(onI.not(), onJ.not(), both))

    model.addEquality(dayI, 0)
    model.addEquality(dayJ, 1)

    val solver = CpSolver()
    assertEquals(CpSolverStatus.OPTIMAL, solver.solve(model))
    assertTrue(solver.booleanValue(both))
  }

  private companion object {
    @BeforeAll
    @JvmStatic
    fun loadNative() {
      Loader.loadNativeLibraries()
    }
  }
}

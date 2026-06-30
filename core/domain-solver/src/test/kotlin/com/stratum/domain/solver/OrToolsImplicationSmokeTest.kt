@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.google.ortools.Loader
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class OrToolsImplicationSmokeTest {
  @Test
  fun boolOrForbidsBothMeaningfulOnConsecutiveAssignment() {
    val model = CpModel()
    val onConsecutive = model.newBoolVar("onConsecutive")
    val meaningfulI = model.newBoolVar("meaningfulI")
    val meaningfulJ = model.newBoolVar("meaningfulJ")
    model.addBoolOr(arrayOf(onConsecutive.not(), meaningfulI.not(), meaningfulJ.not()))

    model.addEquality(onConsecutive, 1)
    model.addEquality(meaningfulI, 1)
    model.addEquality(meaningfulJ, 1)

    val status = CpSolver().solve(model)
    assertEquals(CpSolverStatus.INFEASIBLE, status)
  }

  @Test
  fun implicationWithEquivalentBothMeaningfulForbidsViolation() {
    val model = CpModel()
    val onConsecutive = model.newBoolVar("onConsecutive")
    val meaningfulI = model.newBoolVar("meaningfulI")
    val meaningfulJ = model.newBoolVar("meaningfulJ")
    val bothMeaningful = model.newBoolVar("bothMeaningful")
    model.addBoolAnd(arrayOf(meaningfulI, meaningfulJ)).onlyEnforceIf(bothMeaningful)
    model.addBoolOr(arrayOf(meaningfulI.not(), meaningfulJ.not(), bothMeaningful))
    model.addImplication(onConsecutive, bothMeaningful.not())

    model.addEquality(onConsecutive, 1)
    model.addEquality(meaningfulI, 1)
    model.addEquality(meaningfulJ, 1)

    val status = CpSolver().solve(model)
    assertEquals(CpSolverStatus.INFEASIBLE, status)
  }

  private companion object {
    @BeforeAll
    @JvmStatic
    fun loadNative() {
      Loader.loadNativeLibraries()
    }
  }
}

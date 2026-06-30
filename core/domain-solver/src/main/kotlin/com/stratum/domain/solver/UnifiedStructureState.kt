package com.stratum.domain.solver

import com.google.ortools.sat.IntVar

internal class UnifiedStructureState {
    val domainVarByPosition = mutableMapOf<Int, IntVar>()
}

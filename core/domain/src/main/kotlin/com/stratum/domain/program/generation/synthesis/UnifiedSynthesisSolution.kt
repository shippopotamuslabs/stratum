package com.stratum.domain.program.generation.synthesis

import com.stratum.domain.program.generation.structure.SessionRegionFilter

data class UnifiedSynthesisSolution(
    val activeSlotIdsBySession: List<Set<String>>,
    val setCountBySessionSlot: Map<Pair<Int, String>, Int>,
    val cycleSlotIndexByPosition: List<Int>,
    /** Solver-chosen [SessionRegionFilter] per session position (source of truth). */
    val regionFilterBySession: List<SessionRegionFilter>,
)

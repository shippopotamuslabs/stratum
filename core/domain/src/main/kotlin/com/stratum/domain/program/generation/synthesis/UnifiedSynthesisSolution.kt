package com.stratum.domain.program.generation.synthesis

data class UnifiedSynthesisSolution(
    val activeSlotIdsBySession: List<Set<String>>,
    val setCountBySessionSlot: Map<Pair<Int, String>, Int>,
    val weekdayIndexByPosition: List<Int>,
    val domainBySession: List<SynthesisSessionDomain>,
)

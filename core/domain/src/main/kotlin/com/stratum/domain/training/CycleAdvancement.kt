package com.stratum.domain.training

import com.stratum.domain.program.identity.ProgramId

data class CyclePointerUpdate(
    val programId: ProgramId,
    val currentCycleNumber: Int,
    val cyclePosition: Int,
    /** New cycle start epoch day when the cycle rolls; null when unchanged / unknown. */
    val cycleStartEpochDay: Long? = null,
)

/**
 * Outcome of [com.stratum.domain.program.Program.advanceCycle]: the pointer update
 * `:core:data` applies transactionally, plus the cycle-completed event when the
 * cycle rolled (null otherwise). The advancement decision itself lives on
 * `Program` (STR-167); this type is only its result.
 */
data class CycleAdvancementEffects(
    val pointerUpdate: CyclePointerUpdate,
    val cycleCompletedEvent: ProgramChangeEvent?,
)

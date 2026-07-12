package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.slots.SessionComposition
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate

/**
 * Dual-write companion to [com.stratum.domain.program.generation.prescription.SessionSlotPlan]
 * for wire codec / cycleLength consumers.
 *
 * [sessionType] is a **legacy display label** (mapped from [com.stratum.domain.program.generation.structure.SessionRegionFilter]
 * at materialize time). Structure truth is `regionFilterBySession` on the synthesis result.
 */
data class ResolvedSessionPlan(
    val sessionType: SessionTypeId,
    val name: String,
    val slots: List<WorkoutSlotTemplate>,
    val siblingIndex: Int,
    val position: Int,
    val composition: SessionComposition? = null,
)

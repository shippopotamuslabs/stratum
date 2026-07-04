package com.stratum.domain.program.generation.split

data class ResolvedSessionPlan(
    val sessionType: SessionTypeId,
    val name: String,
    val slots: List<WorkoutSlotTemplate>,
    val siblingIndex: Int,
    val position: Int,
    val composition: SessionComposition? = null,
)

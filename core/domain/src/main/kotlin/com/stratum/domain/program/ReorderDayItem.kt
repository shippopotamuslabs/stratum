package com.stratum.domain.program

/** One cycle-slot row for the reorder-workouts UI (train or rest). */
data class ReorderDayItem(
    val dayId: String,
    val name: String,
)

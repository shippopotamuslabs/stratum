package com.stratum.feature.workout.ui

/**
 * Presentation model for the end-of-workout template change summary (STR-125).
 * Primitives only — no domain types (ARCHITECTURE.md §4.3).
 */
enum class TemplateChangeCategoryUi {
    Lifts,
    Order,
    Sets,
    Rest,
    Supersets,
    Notes,
}

/**
 * One old → new row. A null [oldText] means added (no old value); a null [newText]
 * means removed (no new value). At least one side is always present.
 */
data class TemplateChangeRowUi(
    val label: String,
    val oldText: String?,
    val newText: String?,
)

/** Non-empty group of rows for one category; empty categories are omitted upstream. */
data class TemplateChangeGroupUi(
    val category: TemplateChangeCategoryUi,
    val rows: List<TemplateChangeRowUi>,
)

data class TemplateChangeSummaryUi(
    val groups: List<TemplateChangeGroupUi>,
) {
    val rowCount: Int get() = groups.sumOf { it.rows.size }
    val isEmpty: Boolean get() = groups.isEmpty()
}

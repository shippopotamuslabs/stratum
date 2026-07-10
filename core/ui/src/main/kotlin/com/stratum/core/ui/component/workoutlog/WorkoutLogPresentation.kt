package com.stratum.core.ui.component.workoutlog

/**
 * Pure presentation models for the shared set-log table used by live workout logging and
 * historical session editing. No domain types (ARCHITECTURE.md §4.3).
 */
data class WorkoutLogLiftUi(
    val key: String,
    val liftName: String,
    val sets: List<WorkoutLogSetUi>,
    val showWeightColumn: Boolean = true,
    val weightHeaderLabel: String = "Weight",
    val supersetGroupId: String? = null,
)

data class WorkoutLogSetUi(
    val setKey: String,
    val positionLabel: String,
    /** Domain set type name for W/D labels — e.g. `"Warmup"`, `"DropSet"`, `"Working"`. */
    val setTypeLabel: String,
    val previousLabel: String = "—",
    val weightText: String = "",
    val weightPlaceholder: String = "",
    val repsText: String = "",
    val repsPlaceholder: String = "",
    val rir: Int = 2,
    val isComplete: Boolean = false,
    val fieldsEnabled: Boolean = true,
    val showWeightInput: Boolean = true,
    val showCheckbox: Boolean = true,
    /**
     * Assisted lifts store negative weight; checkbox gate uses magnitude. Live logging only.
     */
    val assistMode: Boolean = false,
    /** Live recommendation amount used when resolving empty weight on checkbox log. */
    val recommendedWeightAmount: Double = 0.0,
    val recommendedReps: Int = 0,
    val repMin: Int = 0,
)

/** Callbacks for one set row — bag avoids Detekt LongParameterList on the composable. */
data class WorkoutLogSetRowActions(
    val onWeightChange: (String) -> Unit = {},
    val onRepsChange: (String) -> Unit = {},
    val onRirChange: (Int) -> Unit = {},
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    /**
     * When non-null, the row is wrapped in [com.stratum.core.ui.component.StratumSwipeToRevealRow]
     * for swipe-to-delete (same pattern as template Edit Workout).
     */
    val onDelete: (() -> Unit)? = null,
    val deleteContentDescription: String = "Delete set",
)

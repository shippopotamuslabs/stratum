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
    /** Ordered pinned notes rendered as stacked rows on the lift card. */
    val workoutPinnedNotes: List<String> = emptyList(),
    val liftPinnedNotes: List<String> = emptyList(),
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    val unitLabel: String = "lb",
)

data class WorkoutLogSetUi(
    val setKey: String,
    val positionLabel: String,
    /**
     * Set-column chip key. Prefer short chips (`S` / `M` / `MM` / `D` / `W`); longer enum
     * names (`Standard`, `MyoRep`, `DropSet`, `Warmup`) are still accepted by the row badge.
     */
    val setTypeLabel: String,
    val previousLabel: String = "—",
    /**
     * Optional second line under [previousLabel] (e.g. `"2 RIR"`). Null hides the line.
     */
    val previousRirLabel: String? = null,
    val weightText: String = "",
    val weightPlaceholder: String = "",
    val repsText: String = "",
    val repsPlaceholder: String = "",
    val partialRepsText: String = "",
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
    /**
     * Shared complete gate for the row checkbox and keyboard Check.
     * Computed via [canCompleteSetLogFromFields] from drafts + recommendations.
     */
    val canComplete: Boolean = false,
    val isPlateLoaded: Boolean = false,
    val isDoubleSided: Boolean = true,
    val unitLabel: String = "lb",
)

/** Callbacks for one set row — bag avoids Detekt LongParameterList on the composable. */
data class WorkoutLogSetRowActions(
    val onWeightChange: (String) -> Unit = {},
    val onRepsChange: (String) -> Unit = {},
    val onPartialRepsChange: (String) -> Unit = {},
    val onRirChange: (Int) -> Unit = {},
    val onCheckedChange: ((Boolean) -> Unit)? = null,
    /**
     * When non-null, the row is wrapped in [com.stratum.core.ui.component.StratumSwipeToRevealRow]
     * for swipe-to-delete (same pattern as template Edit Workout).
     */
    val onDelete: (() -> Unit)? = null,
    val deleteContentDescription: String = "Delete set",
    /**
     * When non-null, the set-type badge is clickable (live workout / template-style type
     * picker). Null keeps the badge display-only (history, minis).
     */
    val onSetTypeClick: (() -> Unit)? = null,
)

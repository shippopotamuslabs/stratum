package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.SupersetLinkableLift

/**
 * Presentation state for the live workout screen (Slice 7). Domain models never reach
 * composables (ARCHITECTURE.md §4.3).
 */
sealed interface WorkoutUiState {
    data object Loading : WorkoutUiState
    data object NoActiveSession : WorkoutUiState

    data class Loaded(
        val workoutId: String,
        val workoutName: String,
        val lifts: List<RenderedLiftUi>,
        val isFinalizing: Boolean,
        val sessionStartedAtEpochMilli: Long,
        val hasIncompleteLifts: Boolean = false,
        val hasDirtyEdits: Boolean = false,
    ) : WorkoutUiState

    data class Error(val message: String) : WorkoutUiState
}

data class RenderedLiftUi(
    override val workoutLiftId: String,
    override val liftId: String,
    val liftName: String,
    val sets: List<RenderedSetUi>,
    override val supersetGroupId: String? = null,
    override val useRestTimerBetweenLifts: Boolean = false,
    /**
     * Whether the per-set table renders a weight column. False for bodyweight lifts (no
     * external load to log). Derived from the lift's loading modality in [WorkoutUiMapper].
     */
    val showWeightColumn: Boolean = true,
    /**
     * Header label for the weight column — `"Weight"` (weighted), `"Assist"` (assisted), or
     * `""` when [showWeightColumn] is false (bodyweight). Pure presentation copy; the
     * domain `LoadType` never reaches composables (ARCHITECTURE.md §4.3).
     */
    val weightHeaderLabel: String = "Weight",
) : SupersetLinkableLift {
    override val name: String get() = liftName
}

data class RenderedSetUi(
    val slotKey: String,
    val positionLabel: String,
    val setTypeLabel: String,
    val repRangeLabel: String?,
    val rirLabel: String,
    val recommendedWeightLabel: String,
    val recommendedRepsLabel: String,
    val loggedWeightLabel: String?,
    val loggedRepsLabel: String?,
    val isComplete: Boolean,
    val previousWeightLabel: String? = null,
    val rirGoal: Int,
    val repMin: Int,
    val repMax: Int,
    val recommendedWeightAmount: Double,
    val recommendedReps: Int,
    val hasLiveRepsRecommendation: Boolean = false,
    val isProjected: Boolean = false,
    val loggedRir: Int? = null,
    /**
     * Whether this set row shows an editable weight cell. False for bodyweight lifts — the
     * weight lever is absent, so the cell is hidden and the set logs at 0. Derived per-set
     * from the owning lift's loading modality in [WorkoutUiMapper].
     */
    val showWeightInput: Boolean = true,
    /**
     * True when the owning lift is assisted — weights are stored negative but displayed as
     * their absolute value. The cell edits a positive assistance amount; [WorkoutViewModel]
     * negates it before persisting. Controls the checkbox weight-gate (uses magnitude, not
     * sign) and label formatting.
     */
    val assistMode: Boolean = false,
)

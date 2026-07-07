package com.stratum.feature.program.ui

import com.stratum.core.ui.component.SupersetLinkableLift
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.domain.program.ProgressionScheme

/**
 * Presentation state for [EditWorkoutScreen]. Domain models never reach composables
 * (ARCHITECTURE.md §4.3).
 */
sealed interface EditWorkoutUiState {
    data object Loading : EditWorkoutUiState

    data object NotFound : EditWorkoutUiState

    data class Loaded(
        val workoutId: String,
        val workoutName: String,
        val weekdayLabel: String,
        val lifts: List<EditLiftState>,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val programUpperVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val programLowerVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val estimatedDurationMinutes: Int = 0,
        val isReorderMode: Boolean = false,
        val pendingReorderLifts: List<EditLiftReorderState>? = null,
        val isDirty: Boolean = false,
        val isSaving: Boolean = false,
        val saveBlockedReason: SaveBlockedReason? = null,
    ) : EditWorkoutUiState
}

enum class SaveBlockedReason {
    PlaceholderLift,
}

// Slice 8 (D11): scheme toggle asserts against ProgressionScheme values, so the enum
// is carried verbatim rather than mapped to a label.
@Suppress("ForbiddenDomainTypeInUiState")
data class EditLiftState(
    override val workoutLiftId: String,
    override val liftId: String,
    override val name: String,
    val position: Int,
    val sets: List<EditSetState>,
    override val supersetGroupId: String? = null,
    override val useRestTimerBetweenLifts: Boolean = false,
    val isCompound: Boolean = false,
    val progressionScheme: ProgressionScheme = ProgressionScheme.Double,
) : SupersetLinkableLift

data class EditSetState(
    val setId: String,
    val setNumber: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rirGoal: Int,
    val isTopSet: Boolean = true,
)

/** Minimal lift row shown during reorder mode. */
data class EditLiftReorderState(
    val workoutLiftId: String,
    val name: String,
)

internal const val DEFAULT_REST_SECONDS = 120
internal const val PLACEHOLDER_LIFT_NAME = "New Exercise"
internal const val DEFAULT_REP_MIN = 8
internal const val DEFAULT_REP_MAX = 12
internal const val DEFAULT_RIR_GOAL = 2
internal const val SIX_PLUS_RIR_GOAL = 6

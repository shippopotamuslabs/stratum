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
        val lifts: List<EditLiftState>,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val programUpperVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val programLowerVolumeByMuscle: List<StratumMuscleVolumeCardState> = emptyList(),
        val estimatedDurationMinutes: Int = 0,
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
    /** Workout-scoped pin; round-tripped on full save (notes UI lives on the live session). */
    val pinnedNote: String? = null,
) : SupersetLinkableLift

/**
 * Presentation set type for the EditWorkout set-type chip (S / M / MM / D).
 * Mapped to domain [com.stratum.domain.program.SetType] + [com.stratum.domain.workout.myorep.MyoRepKind].
 */
enum class EditSetKind {
    Standard,
    MyoRepFloor,
    MyoRepMatch,
    DropSet,
}

data class EditSetState(
    val setId: String,
    val setNumber: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val rirGoal: Int,
    /** Default false so omitted flags do not accidentally mark every set as top. */
    val isTopSet: Boolean = false,
    val setKind: EditSetKind = EditSetKind.Standard,
)

internal const val DEFAULT_REST_SECONDS = 120
internal const val PLACEHOLDER_LIFT_NAME = "New Exercise"
/** Mirrors [com.stratum.domain.program.ProgressionRepRangePolicy.DEFAULT_REP_MIN]. */
internal const val DEFAULT_REP_MIN = com.stratum.domain.program.ProgressionRepRangePolicy.DEFAULT_REP_MIN
/** Mirrors [com.stratum.domain.program.ProgressionRepRangePolicy.DEFAULT_REP_MAX]. */
internal const val DEFAULT_REP_MAX = com.stratum.domain.program.ProgressionRepRangePolicy.DEFAULT_REP_MAX
internal const val DEFAULT_RIR_GOAL = 2
internal const val SIX_PLUS_RIR_GOAL = 6

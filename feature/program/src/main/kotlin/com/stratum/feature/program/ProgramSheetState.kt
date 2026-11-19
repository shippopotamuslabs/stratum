package com.stratum.feature.program

import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.feature.program.ui.EditWorkoutUiState

/**
 * In-memory edit state for every cycle day at once.
 * Lift mutations stay in [EditWorkoutLiftMutations]; the metrics below are built
 * from the volume cards already stored on each day's edit state, not re-derived
 * through the workout use cases.
 */
internal data class ProgramSheetWorkoutMetrics(
    val dayId: String,
    val setTotal: Int,
    val estimatedDurationMinutes: Int,
    val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
)

internal data class ProgramSheetMetrics(
    val workouts: List<ProgramSheetWorkoutMetrics>,
    val programUpperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    val programLowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
)

/**
 * The day's edit state when the day is an editable workout; null for rest days
 * and days without an edit state. Read paths use this guard. Writes go through
 * [updateProgramDay], which also clears the rest flag on the day it writes.
 */
internal fun CreateProgramDayUi.sheetEditOrNull(): EditWorkoutUiState.Loaded? =
    if (isRest) null else editState

internal fun updateProgramDay(
    days: List<CreateProgramDayUi>,
    dayId: String,
    transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded,
): List<CreateProgramDayUi> =
    days.map { day ->
        val edit = day.editState
        if (day.id != dayId || edit == null) {
            day
        } else {
            day.copy(editState = transform(edit), isRest = false)
        }
    }

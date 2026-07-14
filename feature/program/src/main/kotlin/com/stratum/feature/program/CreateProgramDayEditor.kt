package com.stratum.feature.program

import com.stratum.feature.program.ui.EditWorkoutUiState
import java.util.UUID

/**
 * Pure day-list mutations for the create/edit program editor.
 * Keeps [CreateProgramViewModel] free of inline list surgery for add/remove/reorder/rename.
 */
internal object CreateProgramDayEditor {

    fun addRestDay(state: CreateProgramUiState): CreateProgramUiState {
        if (!state.canAddDay) return state
        val id = UUID.randomUUID().toString()
        val position = state.days.size
        val day =
            CreateProgramDayUi(
                id = id,
                name = "Rest",
                position = position,
                isRest = true,
                editState = null,
            )
        return state.copy(
            days = state.days + day,
            selectedDayId = id,
            isDirty = true,
        )
    }

    fun removeSelectedDay(state: CreateProgramUiState): CreateProgramUiState {
        val selectedId = state.selectedDayId ?: return state
        if (state.days.size <= 1) return state
        val remaining =
            state.days
                .filterNot { day -> day.id == selectedId }
                .mapIndexed { index, day -> day.copy(position = index) }
        return state.copy(
            days = remaining,
            selectedDayId = remaining.firstOrNull()?.id,
            isDirty = true,
        )
    }

    /**
     * Reorders cycle days by stable [orderedDayIds] (from local reorder confirm).
     * Returns [state] unchanged when the id set mismatches or order is already applied
     * (including dirty flag).
     */
    fun reorderDaysByIds(
        state: CreateProgramUiState,
        orderedDayIds: List<String>,
    ): CreateProgramUiState {
        if (orderedDayIds.size != state.days.size) return state
        val byId = state.days.associateBy { day -> day.id }
        if (orderedDayIds.any { id -> id !in byId }) return state
        if (orderedDayIds == state.days.map { day -> day.id }) return state
        val reordered = orderedDayIds.map { id -> byId.getValue(id) }
        return state.copy(
            days = reordered.mapIndexed { index, day -> day.copy(position = index) },
            isDirty = true,
        )
    }

    fun renameSelectedWorkout(
        state: CreateProgramUiState,
        name: String,
        emptyEdit: (workoutId: String, name: String) -> EditWorkoutUiState.Loaded,
    ): CreateProgramUiState {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return state
        val selectedId = state.selectedDayId ?: return state
        return state.copy(
            days =
                state.days.map { day ->
                    if (day.id != selectedId) {
                        day
                    } else {
                        day.copy(
                            name = trimmed,
                            isRest = false,
                            editState =
                                day.editState?.copy(workoutName = trimmed)
                                    ?: emptyEdit(day.id, trimmed),
                        )
                    }
                },
            showRenameWorkout = false,
            isDirty = true,
        )
    }

    fun updateSelectedWorkout(
        state: CreateProgramUiState,
        transform: (EditWorkoutUiState.Loaded) -> EditWorkoutUiState.Loaded,
    ): CreateProgramUiState {
        val selectedId = state.selectedDayId ?: return state
        return state.copy(
            days =
                state.days.map { day ->
                    if (day.id != selectedId || day.editState == null) {
                        day
                    } else {
                        day.copy(editState = transform(day.editState), isRest = false)
                    }
                },
            isDirty = true,
        )
    }
}

package com.stratum.feature.history.ui

import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi

sealed interface EditHistoricalWorkoutUiState {
    data object Loading : EditHistoricalWorkoutUiState

    data object Error : EditHistoricalWorkoutUiState

    data class Loaded(
        val workoutName: String,
        val lifts: List<WorkoutLogLiftUi>,
        val isDirty: Boolean,
        val isSaving: Boolean,
    ) : EditHistoricalWorkoutUiState
}

sealed interface EditHistoricalWorkoutEvent {
    data object NavigateBack : EditHistoricalWorkoutEvent
}

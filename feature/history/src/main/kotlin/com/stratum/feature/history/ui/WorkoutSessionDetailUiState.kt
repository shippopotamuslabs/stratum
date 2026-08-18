package com.stratum.feature.history.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState

import java.time.LocalDate
import java.time.LocalTime

sealed interface WorkoutSessionDetailUiState {
    data object Loading : WorkoutSessionDetailUiState
    data object Error : WorkoutSessionDetailUiState

    data class Loaded(
        val workoutName: String,
        val startDate: LocalDate,
        val startTime: LocalTime,
        val finishDate: LocalDate,
        val finishTime: LocalTime,
        val completedAtLabel: String,
        val durationFormatted: String,
        val totalSetsLabel: String,
        val personalRecordsLabel: String,
        val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
        val lifts: List<HistoryLiftUi>,
    ) : WorkoutSessionDetailUiState
}

data class HistoryLiftUi(
    val liftName: String,
    val sets: List<HistorySetUi>,
)

data class HistorySetUi(
    val setIndex: Int,
    val performanceLabel: String,
    /** Logged RIR for the set; null when not recorded. */
    val rir: Int?,
    val isPersonalRecord: Boolean,
)

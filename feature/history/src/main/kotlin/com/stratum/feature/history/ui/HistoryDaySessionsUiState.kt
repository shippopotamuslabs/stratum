package com.stratum.feature.history.ui

import com.stratum.core.ui.component.StratumMuscleVolumeCardState

sealed interface HistoryDaySessionsUiState {
    data object Loading : HistoryDaySessionsUiState
    data object Error : HistoryDaySessionsUiState

    data class Loaded(
        val dateTitle: String,
        val sessions: List<HistoryDaySessionSummaryUi>,
    ) : HistoryDaySessionsUiState
}

/**
 * Presentation summary for one completed workout on a multi-session day list card.
 * Mirrors the session detail header (name, time, stats, volume) without lift rows.
 */
data class HistoryDaySessionSummaryUi(
    val sessionId: String,
    val workoutName: String,
    val completedAtLabel: String,
    val durationFormatted: String,
    val totalSetsLabel: String,
    val personalRecordsLabel: String,
    val upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    val lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
)

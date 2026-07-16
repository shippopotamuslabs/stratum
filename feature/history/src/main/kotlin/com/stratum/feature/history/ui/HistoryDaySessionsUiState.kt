package com.stratum.feature.history.ui

sealed interface HistoryDaySessionsUiState {
    data object Loading : HistoryDaySessionsUiState
    data object Error : HistoryDaySessionsUiState

    data class Loaded(
        val dateTitle: String,
        val sessions: List<HistoryDaySessionSummaryUi>,
    ) : HistoryDaySessionsUiState
}

/**
 * Compact presentation summary for one completed workout on a multi-session day list card.
 * Stats only (no interactive volume row — volume lives on session detail).
 */
data class HistoryDaySessionSummaryUi(
    val sessionId: String,
    val workoutName: String,
    val completedAtLabel: String,
    val durationFormatted: String,
    val totalSetsLabel: String,
    val personalRecordsLabel: String,
)

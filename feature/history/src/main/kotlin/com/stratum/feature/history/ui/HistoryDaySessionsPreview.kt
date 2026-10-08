package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

@PreviewLightDark
@Composable
private fun HistoryDaySessionsLoadedTonalPreview() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewDaySessionsLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun HistoryDaySessionsLoadedNeoPreview() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewDaySessionsLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun HistoryDaySessionsErrorTonalPreview() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.Tonal,
        state = HistoryDaySessionsUiState.Error,
    )
}

@PreviewLightDark
@Composable
private fun HistoryDaySessionsErrorNeoPreview() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = HistoryDaySessionsUiState.Error,
    )
}

@Composable
internal fun HistoryDaySessionsPreviewContent(
    family: StratumThemeId,
    state: HistoryDaySessionsUiState,
) {
    StratumTheme(themeId = family) {
        HistoryDaySessionsScreen(
            state = state,
            onBackClick = {},
            onSessionClick = {},
        )
    }
}

internal fun previewDaySessionsLoaded(): HistoryDaySessionsUiState.Loaded =
    HistoryDaySessionsUiState.Loaded(
        dateTitle = "Sunday, Aug 10",
        sessions =
            listOf(
                HistoryDaySessionSummaryUi(
                    sessionId = "session-1",
                    workoutName = "Push Day",
                    completedAtLabel = "9:38 PM",
                    durationFormatted = "45 min",
                    totalSetsLabel = "12 sets",
                    personalRecordsLabel = "1 PR",
                ),
                HistoryDaySessionSummaryUi(
                    sessionId = "session-2",
                    workoutName = "Bonus Arms",
                    completedAtLabel = "7:05 PM",
                    durationFormatted = "20 min",
                    totalSetsLabel = "6 sets",
                    personalRecordsLabel = "0 PRs",
                ),
            ),
    )

package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalDate
import java.time.LocalTime

/**
 * Screenshot tests for history (STR-95). Thin `@PreviewTest` wrappers over the shared preview
 * content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun historyMonthTonal() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewHistoryMonth(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyMonthNeo() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewHistoryMonth(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyYearTonal() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewHistoryYear(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyYearNeo() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewHistoryYear(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyErrorTonal() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = WorkoutHistoryUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyErrorNeo() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = WorkoutHistoryUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyDaySessionsLoadedTonal() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewDaySessionsLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyDaySessionsLoadedNeo() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewDaySessionsLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyDaySessionsErrorTonal() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.Tonal,
        state = HistoryDaySessionsUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun historyDaySessionsErrorNeo() {
    HistoryDaySessionsPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = HistoryDaySessionsUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun sessionDetailLoadedTonal() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSessionDetailLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun sessionDetailLoadedNeo() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewSessionDetailLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun sessionDetailErrorTonal() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.Tonal,
        state = WorkoutSessionDetailUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun sessionDetailErrorNeo() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = WorkoutSessionDetailUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editHistoricalLoadedTonal() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewEditHistoricalLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editHistoricalLoadedNeo() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewEditHistoricalLoaded(),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editHistoricalErrorTonal() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.Tonal,
        state = EditHistoricalWorkoutUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editHistoricalErrorNeo() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = EditHistoricalWorkoutUiState.Error,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutTimeTonal() {
    EditWorkoutTimePreviewContent(
        family = StratumThemeId.Tonal,
        initialStartTime = LocalTime.of(20, 53),
        initialFinishDate = LocalDate.of(2025, 8, 10),
        initialFinishTime = LocalTime.of(21, 38),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutTimeNeo() {
    EditWorkoutTimePreviewContent(
        family = StratumThemeId.NeoBrutalist,
        initialStartTime = LocalTime.of(22, 0),
        initialFinishDate = LocalDate.of(2025, 8, 11),
        initialFinishTime = LocalTime.of(0, 30),
    )
}

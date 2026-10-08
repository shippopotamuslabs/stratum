package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalDate
import java.time.YearMonth

@PreviewLightDark
@Composable
private fun WorkoutHistoryMonthTonalPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewHistoryMonth(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutHistoryMonthNeoPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewHistoryMonth(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutHistoryYearTonalPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewHistoryYear(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutHistoryYearNeoPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewHistoryYear(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutHistoryErrorTonalPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.Tonal,
        state = WorkoutHistoryUiState.Error,
    )
}

@PreviewLightDark
@Composable
private fun WorkoutHistoryErrorNeoPreview() {
    WorkoutHistoryPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = WorkoutHistoryUiState.Error,
    )
}

@Composable
internal fun WorkoutHistoryPreviewContent(
    family: StratumThemeId,
    state: WorkoutHistoryUiState,
) {
    StratumTheme(themeId = family) {
        WorkoutHistoryScreen(
            state = state,
            onBackClick = {},
            onModeSelected = {},
            onMonthClick = {},
            onYearClick = {},
            onDayClick = {},
            onScrollToMonthConsumed = {},
            onScrollToYearConsumed = {},
        )
    }
}

internal fun previewHistoryMonth(): WorkoutHistoryUiState.Loaded {
    val yearMonth = YearMonth.of(2025, 8)
    val workoutDays = mapOf(1 to "Push Day", 3 to "Pull Day", 5 to "Legs", 8 to "Push Day")
    val days =
        (1..31).map { day ->
            val label = workoutDays[day]
            HistoryDayUi(
                date = LocalDate.of(2025, 8, day),
                dayOfMonth = day,
                hasWorkout = label != null,
                workoutLabel = label,
                sessionIds = if (label != null) listOf("session-$day") else emptyList(),
            )
        }
    return WorkoutHistoryUiState.Loaded(
        mode = HistoryCalendarMode.Month,
        weekStreak = 6,
        restDaysCount = 2,
        months =
            listOf(
                HistoryMonthUi(
                    yearMonth = yearMonth,
                    leadingEmptyDays = 5,
                    days = days,
                ),
            ),
        years = emptyList(),
    )
}

internal fun previewHistoryYear(): WorkoutHistoryUiState.Loaded =
    WorkoutHistoryUiState.Loaded(
        mode = HistoryCalendarMode.Year,
        weekStreak = 6,
        restDaysCount = 2,
        months = emptyList(),
        years =
            listOf(
                HistoryYearUi(
                    year = 2025,
                    months =
                        (1..12).map { month ->
                            HistoryMiniMonthUi(
                                yearMonth = YearMonth.of(2025, month),
                                leadingEmptyDays = 0,
                                dayCount = 28,
                                workoutDays = setOf(1, 8, 15),
                            )
                        },
                ),
            ),
    )

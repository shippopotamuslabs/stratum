package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetUi
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.LocalDate
import java.time.LocalTime
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

@PreviewLightDark
@Composable
private fun WorkoutSessionDetailLoadedTonalPreview() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewSessionDetailLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutSessionDetailLoadedNeoPreview() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewSessionDetailLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun WorkoutSessionDetailErrorTonalPreview() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.Tonal,
        state = WorkoutSessionDetailUiState.Error,
    )
}

@PreviewLightDark
@Composable
private fun WorkoutSessionDetailErrorNeoPreview() {
    WorkoutSessionDetailPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = WorkoutSessionDetailUiState.Error,
    )
}

@PreviewLightDark
@Composable
private fun EditHistoricalWorkoutLoadedTonalPreview() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewEditHistoricalLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun EditHistoricalWorkoutLoadedNeoPreview() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewEditHistoricalLoaded(),
    )
}

@PreviewLightDark
@Composable
private fun EditHistoricalWorkoutErrorTonalPreview() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.Tonal,
        state = EditHistoricalWorkoutUiState.Error,
    )
}

@PreviewLightDark
@Composable
private fun EditHistoricalWorkoutErrorNeoPreview() {
    EditHistoricalWorkoutPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = EditHistoricalWorkoutUiState.Error,
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

@Composable
internal fun WorkoutSessionDetailPreviewContent(
    family: StratumThemeId,
    state: WorkoutSessionDetailUiState,
) {
    StratumTheme(themeId = family) {
        WorkoutSessionDetailScreen(
            state = state,
            onBackClick = {},
            onEditWorkoutClick = {},
        )
    }
}

@Composable
internal fun EditHistoricalWorkoutPreviewContent(
    family: StratumThemeId,
    state: EditHistoricalWorkoutUiState,
) {
    StratumTheme(themeId = family) {
        EditHistoricalWorkoutScreen(
            state = state,
            onBackClick = {},
            onConfirmClick = {},
            onWeightChange = { _, _, _ -> },
            onRepsChange = { _, _, _ -> },
            onPartialRepsChange = { _, _, _ -> },
            onRirChange = { _, _, _ -> },
            onAddSet = {},
            onDeleteSet = { _, _ -> },
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

internal fun previewSessionDetailLoaded(): WorkoutSessionDetailUiState.Loaded =
    WorkoutSessionDetailUiState.Loaded(
        workoutName = "Push Day",
        startDate = LocalDate.of(2025, 8, 10),
        startTime = LocalTime.of(20, 53),
        finishDate = LocalDate.of(2025, 8, 10),
        finishTime = LocalTime.of(21, 38),
        completedAtLabel = "Aug 10 · 9:38 PM",
        durationFormatted = "45 min",
        totalSetsLabel = "12 sets",
        personalRecordsLabel = "1 PR",
        personalRecordCount = 1,
        upperVolumeByMuscle =
            listOf(
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Chest,
                    displayName = MuscleGroup.Chest.displayLabel,
                    exerciseCount = 2,
                    setCount = 8.0,
                ),
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Tricep,
                    displayName = MuscleGroup.Tricep.displayLabel,
                    exerciseCount = 1,
                    setCount = 4.0,
                ),
            ),
        lowerVolumeByMuscle = emptyList(),
        lifts =
            listOf(
                HistoryLiftUi(
                    liftName = "Barbell Bench Press",
                    sets =
                        listOf(
                            HistorySetUi(
                                setIndex = 1,
                                performanceLabel = "225 lb × 5",
                                rir = 2,
                                isPersonalRecord = true,
                            ),
                            HistorySetUi(
                                setIndex = 2,
                                performanceLabel = "225 lb × 4",
                                rir = 1,
                                isPersonalRecord = false,
                            ),
                        ),
                    bestSet =
                        HistorySetUi(
                            setIndex = 1,
                            performanceLabel = "225 lb × 5",
                            rir = 2,
                            isPersonalRecord = true,
                        ),
                ),
                HistoryLiftUi(
                    liftName = "Overhead Press",
                    sets =
                        listOf(
                            HistorySetUi(
                                setIndex = 1,
                                performanceLabel = "135 lb × 8",
                                rir = 2,
                                isPersonalRecord = false,
                            ),
                        ),
                ),
            ),
    )

internal fun previewEditHistoricalLoaded(): EditHistoricalWorkoutUiState.Loaded =
    EditHistoricalWorkoutUiState.Loaded(
        workoutName = "Push Day",
        lifts =
            listOf(
                WorkoutLogLiftUi(
                    key = "lift-1",
                    liftName = "Barbell Bench Press",
                    sets =
                        listOf(
                            WorkoutLogSetUi(
                                setKey = "set-1",
                                positionLabel = "1",
                                setTypeLabel = "S",
                                previousLabel = "225 × 5",
                                weightText = "225",
                                repsText = "5",
                                rir = 2,
                                isComplete = true,
                            ),
                            WorkoutLogSetUi(
                                setKey = "set-2",
                                positionLabel = "2",
                                setTypeLabel = "S",
                                previousLabel = "225 × 4",
                                weightText = "225",
                                repsText = "",
                                rir = 2,
                            ),
                        ),
                ),
            ),
        isDirty = true,
        isSaving = false,
    )

package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup
import java.time.LocalDate
import java.time.LocalTime

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

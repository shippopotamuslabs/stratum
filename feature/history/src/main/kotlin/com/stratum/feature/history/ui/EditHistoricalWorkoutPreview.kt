package com.stratum.feature.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.workoutlog.WorkoutLogLiftUi
import com.stratum.core.ui.component.workoutlog.WorkoutLogSetUi
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

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

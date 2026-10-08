package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Screenshot-test previews for [EditWorkoutScreen] (STR-11): theme families × light/dark plus
 * the non-loaded route states. (The loaded-light preview lives in `EditWorkoutScreen.kt`.)
 *
 * `darkTheme` is left null so `@PreviewLightDark` genuinely varies light/dark.
 */
@PreviewLightDark
@Composable
private fun EditWorkoutScreenLoadedTonalPreview() {
    EditWorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutScreenLoadedNeoPreview() {
    EditWorkoutScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun EditWorkoutScreenLoadingTonalPreview() {
    EditWorkoutLoadingPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun EditWorkoutScreenNotFoundTonalPreview() {
    EditWorkoutNotFoundPreviewContent(family = StratumThemeId.Tonal)
}

@Composable
internal fun EditWorkoutLoadingPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutScreen(
            state = EditWorkoutUiState.Loading,
            onLeave = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

@Composable
internal fun EditWorkoutNotFoundPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutScreen(
            state = EditWorkoutUiState.NotFound,
            onLeave = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

@Composable
internal fun EditWorkoutScreenPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        EditWorkoutScreen(
            showStartWorkout = true,
            state = previewEditWorkoutLoadedState(),
            onLeave = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

internal fun previewEditWorkoutLoadedState(): EditWorkoutUiState.Loaded =
    EditWorkoutUiState.Loaded(
        workoutId = "w-1",
        workoutName = "Push Day",
        upperVolumeByMuscle =
            listOf(
                StratumMuscleVolumeCardState(
                    muscle = MuscleGroup.Chest,
                    displayName = "Chest",
                    exerciseCount = 1,
                    setCount = 2.0,
                ),
            ),
        lowerVolumeByMuscle = emptyList(),
        estimatedDurationMinutes = 4,
        lifts =
            listOf(
                EditLiftState(
                    workoutLiftId = "wl-1",
                    liftId = "bench-press",
                    name = "Barbell Bench Press",
                    position = 0,
                    sets =
                        listOf(
                            EditSetState(
                                setId = "s-1",
                                setNumber = 1,
                                repMin = 5,
                                repMax = 5,
                                restSeconds = 120,
                                rirGoal = 2,
                                isTopSet = true,
                            ),
                            EditSetState(
                                setId = "s-2",
                                setNumber = 2,
                                repMin = 0,
                                repMax = 0,
                                restSeconds = 120,
                                rirGoal = 2,
                                isTopSet = false,
                            ),
                        ),
                ),
            ),
    )

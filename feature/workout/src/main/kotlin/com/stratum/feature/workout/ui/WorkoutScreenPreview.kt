package com.stratum.feature.workout.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot-test previews for the live workout route states (STR-11).
 *
 * Determinism rules: the elapsed clock reads `sessionStartedAtEpochMilli`, so previews pin it
 * to composition time (`00:00`); the rest timer stays inactive (no in-flight animation).
 * `darkTheme` is left null so `@PreviewLightDark` genuinely varies light/dark.
 */
@PreviewLightDark
@Composable
private fun WorkoutScreenTonalPreview() {
    WorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutScreenNeoPreview() {
    WorkoutScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutScreenNoActiveSessionTonalPreview() {
    WorkoutNoActiveSessionPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutScreenNoActiveSessionNeoPreview() {
    WorkoutNoActiveSessionPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewLightDark
@Composable
private fun WorkoutScreenErrorTonalPreview() {
    WorkoutErrorPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun WorkoutScreenLoadingTonalPreview() {
    WorkoutLoadingPreviewContent(family = StratumThemeId.Tonal)
}

@Composable
internal fun WorkoutNoActiveSessionPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        NoActiveSessionContent(
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Composable
internal fun WorkoutErrorPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        ErrorContent(
            message = "Session failed to load",
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@Composable
internal fun WorkoutLoadingPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        LoadingContent(snackbarHostState = remember { SnackbarHostState() })
    }
}

@Composable
internal fun WorkoutScreenPreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        WorkoutScreen(
            state = previewWorkoutLoadedState(),
            restTimer = previewRestTimerChrome(),
            actions = previewWorkoutScreenActions(),
        )
    }
}

internal fun previewRestTimerChrome(
    isActive: Boolean = false,
    remainingSeconds: Int = 0,
    totalSeconds: Int = 0,
): RestTimerChrome =
    RestTimerChrome(
        isActive = isActive,
        remainingSeconds = remainingSeconds,
        totalSeconds = totalSeconds,
        onStart = {},
        onAdd10s = {},
        onSubtract10s = {},
        onSkip = {},
    )

internal fun previewWorkoutScreenActions(): WorkoutScreenActions =
    WorkoutScreenActions(
        onNavigateBack = {},
        onCompleteWorkout = {},
        onAbandon = {},
        onLogSet = { _, _, _, _, _ -> },
        onUnlogSet = {},
        onSwapLiftClick = { _, _, _, _ -> },
        onLinkSupersetWithPrevious = {},
        onLinkSupersetWithNext = {},
        onRemoveFromSuperset = {},
        onSetUseRestTimerBetweenLifts = { _, _ -> },
        onRemoveLift = {},
        onReorderLiftsClick = {},
    )

internal fun previewWorkoutLoadedState(): WorkoutUiState.Loaded =
    WorkoutUiState.Loaded(
        workoutId = "w-1",
        workoutName = "Lower A",
        lifts =
            listOf(
                RenderedLiftUi(
                    workoutLiftId = "wl-1",
                    liftId = "back-squat",
                    liftName = "Back Squat",
                    navigatorInitials = "BS",
                    sessionEquipmentId = "barbell",
                    sessionEquipmentName = "Olympic Barbell",
                    sets =
                        listOf(
                            RenderedSetUi(
                                slotKey = "wl-1:s1",
                                positionLabel = "1",
                                setTypeLabel = "S",
                                repRangeLabel = "5",
                                rirLabel = "2 RIR",
                                recommendedWeightLabel = "185 lb",
                                recommendedRepsLabel = "5",
                                loggedWeightLabel = "185 lb",
                                loggedRepsLabel = "5",
                                isComplete = true,
                                rirGoal = 2,
                                repMin = 5,
                                repMax = 5,
                                recommendedWeightAmount = 185.0,
                                recommendedReps = 5,
                            ),
                            RenderedSetUi(
                                slotKey = "wl-1:s2",
                                positionLabel = "2",
                                setTypeLabel = "S",
                                repRangeLabel = "5",
                                rirLabel = "2 RIR",
                                recommendedWeightLabel = "185 lb",
                                recommendedRepsLabel = "5",
                                loggedWeightLabel = null,
                                loggedRepsLabel = null,
                                isComplete = false,
                                rirGoal = 2,
                                repMin = 5,
                                repMax = 5,
                                recommendedWeightAmount = 185.0,
                                recommendedReps = 5,
                            ),
                        ),
                ),
                RenderedLiftUi(
                    workoutLiftId = "wl-2",
                    liftId = "pull-up",
                    liftName = "Pull-Up",
                    navigatorInitials = "PU",
                    showWeightColumn = false,
                    weightHeaderLabel = "",
                    sets =
                        listOf(
                            RenderedSetUi(
                                slotKey = "wl-2:s1",
                                positionLabel = "1",
                                setTypeLabel = "S",
                                repRangeLabel = "8",
                                rirLabel = "1 RIR",
                                recommendedWeightLabel = "",
                                recommendedRepsLabel = "8",
                                loggedWeightLabel = null,
                                loggedRepsLabel = null,
                                isComplete = false,
                                showWeightInput = false,
                                rirGoal = 1,
                                repMin = 8,
                                repMax = 8,
                                recommendedWeightAmount = 0.0,
                                recommendedReps = 8,
                            ),
                        ),
                ),
            ),
        isFinalizing = false,
        // Composition time: the elapsed clock renders 00:00 deterministically.
        sessionStartedAtEpochMilli = System.currentTimeMillis(),
        hasIncompleteLifts = true,
    )

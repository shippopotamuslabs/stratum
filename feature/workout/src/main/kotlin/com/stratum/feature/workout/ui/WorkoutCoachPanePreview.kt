package com.stratum.feature.workout.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Preview and screenshot content for the coach pane (STR-323). Fake state lives
 * here so Studio previews and goldens can never drift apart.
 */
internal fun previewCoachState(): WorkoutUiState.Loaded {
    val base = previewWorkoutLoadedState()
    return base.copy(
        lifts =
            base.lifts.mapIndexed { index, lift ->
                if (index != 0) {
                    lift
                } else {
                    lift.copy(
                        sets =
                            lift.sets.map { set ->
                                set.copy(
                                    previousWeightLabel = "185 lb × 5",
                                    previousRirLabel = "2 RIR",
                                    previousWeightAmount = 185.0,
                                    previousReps = 5,
                                    loggedWeightAmount = if (set.isComplete) 185.0 else null,
                                    loggedReps = if (set.isComplete) 5 else null,
                                    restSeconds = 150,
                                )
                            },
                    )
                }
            },
    )
}

@Composable
internal fun WorkoutCoachPanePreviewContent(
    family: StratumThemeId,
    tab: WorkoutCoachTab,
) {
    StratumTheme(themeId = family) {
        WorkoutCoachPane(
            state = previewCoachState(),
            primaryLiftId = "wl-1",
            restTimer =
                previewRestTimerChrome(
                    isActive = tab == WorkoutCoachTab.Rest,
                    remainingSeconds = 84,
                    totalSeconds = 150,
                ),
            tab = tab,
            onTabChange = {},
        )
    }
}

@Composable
internal fun WorkoutCoachTwoPanePreviewContent(family: StratumThemeId) {
    StratumTheme(themeId = family) {
        Row {
            WorkoutScreen(
                state = previewCoachState(),
                restTimer = previewRestTimerChrome(),
                actions = previewWorkoutScreenActions(),
                modifier = Modifier.weight(1f),
            )
            WorkoutCoachPane(
                state = previewCoachState(),
                primaryLiftId = "wl-1",
                restTimer = previewRestTimerChrome(),
                tab = WorkoutCoachTab.Coach,
                onTabChange = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

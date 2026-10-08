package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the live workout route states (STR-11). Thin `@PreviewTest` wrappers
 * over the shared preview content in `WorkoutScreenPreview.kt` — fake state lives there so
 * Studio `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLoadedTonal() {
    WorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLoadedNeo() {
    WorkoutScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenNoActiveSessionTonal() {
    WorkoutNoActiveSessionPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenNoActiveSessionNeo() {
    WorkoutNoActiveSessionPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenErrorTonal() {
    WorkoutErrorPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLoadingTonal() {
    WorkoutLoadingPreviewContent(family = StratumThemeId.Tonal)
}

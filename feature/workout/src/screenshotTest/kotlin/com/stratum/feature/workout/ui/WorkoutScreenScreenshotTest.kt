package com.stratum.feature.workout.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumTheme
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
    StratumTheme(themeId = StratumThemeId.Tonal) {
        NoActiveSessionContent(
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenNoActiveSessionNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        NoActiveSessionContent(
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenErrorTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        ErrorContent(
            message = "Session failed to load",
            onNavigateBack = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutScreenLoadingTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        LoadingContent(snackbarHostState = remember { SnackbarHostState() })
    }
}

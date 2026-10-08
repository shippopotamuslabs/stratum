package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for [EditWorkoutScreen] (STR-11). Thin `@PreviewTest` wrappers over the
 * shared preview content in `EditWorkoutScreenPreview.kt` — fake state lives there so Studio
 * `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenLoadedTonal() {
    EditWorkoutScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenLoadedNeo() {
    EditWorkoutScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenLoadingTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        EditWorkoutScreen(
            state = EditWorkoutUiState.Loading,
            onLeave = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenNotFoundTonal() {
    StratumTheme(themeId = StratumThemeId.Tonal) {
        EditWorkoutScreen(
            state = EditWorkoutUiState.NotFound,
            onLeave = {},
            callbacks = EditWorkoutScreenCallbacks(),
        )
    }
}

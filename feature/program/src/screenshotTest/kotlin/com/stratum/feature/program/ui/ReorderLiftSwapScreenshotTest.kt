package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for reorder + lift-swap screens (STR-92). Thin `@PreviewTest` wrappers
 * over the shared preview content — fake state lives in the main-source previews so
 * Studio `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun reorderLiftsTonal() {
    ReorderLiftsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun reorderLiftsNeo() {
    ReorderLiftsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun reorderWorkoutsTonal() {
    ReorderWorkoutsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun reorderWorkoutsNeo() {
    ReorderWorkoutsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapIdleTonal() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapIdle())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapIdleNeo() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapIdle())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapSearchingTonal() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapSearching())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapSearchingNeo() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapSearching())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapResultsTonal() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapResults())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapResultsNeo() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapResults())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapEmptyTonal() {
    LiftSwapPreviewContent(family = StratumThemeId.Tonal, state = previewSwapEmpty())
}

@PreviewTest
@PreviewLightDark
@Composable
fun liftSwapEmptyNeo() {
    LiftSwapPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSwapEmpty())
}

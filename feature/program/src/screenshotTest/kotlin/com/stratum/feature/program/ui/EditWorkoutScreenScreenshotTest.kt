package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
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
    EditWorkoutLoadingPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenNotFoundTonal() {
    EditWorkoutNotFoundPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenRirSheetTonal() {
    EditWorkoutRirSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenRirSheetNeo() {
    EditWorkoutRirSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenSupersetSheetTonal() {
    EditWorkoutSupersetSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenSupersetSheetNeo() {
    EditWorkoutSupersetSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenLiftActionsTonal() {
    EditWorkoutLiftActionsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenLiftActionsNeo() {
    EditWorkoutLiftActionsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDiscardSheetTonal() {
    EditWorkoutDiscardSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDiscardSheetNeo() {
    EditWorkoutDiscardSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenProgressionExplainerTonal() {
    EditWorkoutProgressionExplainerPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenProgressionExplainerNeo() {
    EditWorkoutProgressionExplainerPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDirtyStartDialogTonal() {
    EditWorkoutDirtyStartDialogPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDirtyStartDialogNeo() {
    EditWorkoutDirtyStartDialogPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenActiveSessionDiscardTonal() {
    EditWorkoutActiveSessionDiscardPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenActiveSessionDiscardNeo() {
    EditWorkoutActiveSessionDiscardPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDirtyTopBarTonal() {
    EditWorkoutDirtyTopBarPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenDirtyTopBarNeo() {
    EditWorkoutDirtyTopBarPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenSavingTonal() {
    EditWorkoutSavingPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun editWorkoutScreenSavingNeo() {
    EditWorkoutSavingPreviewContent(family = StratumThemeId.NeoBrutalist)
}

package com.stratum

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the gym catalog (STR-93). Thin `@PreviewTest` wrappers over the shared
 * preview content in `GymScreenPreview.kt`.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun gymLoadedTonal() {
    GymScreenPreviewContent(family = StratumThemeId.Tonal, state = previewGymLoaded())
}

@PreviewTest
@PreviewLightDark
@Composable
fun gymLoadedNeo() {
    GymScreenPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGymLoaded())
}

@PreviewTest
@PreviewLightDark
@Composable
fun gymDeleteDialogTonal() {
    GymScreenPreviewContent(family = StratumThemeId.Tonal, state = previewGymDeleteDialog())
}

@PreviewTest
@PreviewLightDark
@Composable
fun gymDeleteDialogNeo() {
    GymScreenPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGymDeleteDialog())
}

@PreviewTest
@PreviewLightDark
@Composable
fun gymMergeModeTonal() {
    GymScreenPreviewContent(family = StratumThemeId.Tonal, state = previewGymMergeMode())
}

@PreviewTest
@PreviewLightDark
@Composable
fun gymMergeModeNeo() {
    GymScreenPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGymMergeMode())
}

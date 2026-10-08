package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for Programs + Library screens (STR-92). Thin `@PreviewTest` wrappers
 * over the shared preview content — fake state lives in the main-source previews so
 * Studio `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun programsScreenLoadedTonal() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programsScreenLoadedNeo() {
    ProgramsLoadedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programsScreenEmptyTonal() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programsScreenEmptyNeo() {
    ProgramsEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryLoadedTonal() {
    ProgramLibraryLoadedPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryLoadedNeo() {
    ProgramLibraryLoadedPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryEmptyTonal() {
    ProgramLibraryEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryEmptyNeo() {
    ProgramLibraryEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
}

package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for CheckInScreen phases (STR-92). Thin `@PreviewTest` wrappers over
 * the shared preview content — fake state lives in the main-source previews so Studio
 * `@Preview`s and goldens can never drift apart. The swap picker pins its body inline
 * (dialog windows are not capturable) — see docs/screenshot-tests.md.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun checkInSubjectiveTonal() {
    CheckInPreviewContent(family = StratumThemeId.Tonal, state = subjectiveBaseState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInSubjectiveNeo() {
    CheckInPreviewContent(family = StratumThemeId.NeoBrutalist, state = subjectiveBaseState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInLocalizedMusclesTonal() {
    CheckInPreviewContent(family = StratumThemeId.Tonal, state = subjectiveLocalizedState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInLocalizedMusclesNeo() {
    CheckInPreviewContent(family = StratumThemeId.NeoBrutalist, state = subjectiveLocalizedState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInRecommendationsTonal() {
    CheckInPreviewContent(family = StratumThemeId.Tonal, state = recommendationsMixedState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInRecommendationsNeo() {
    CheckInPreviewContent(family = StratumThemeId.NeoBrutalist, state = recommendationsMixedState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInRecommendationsEmptyTonal() {
    CheckInPreviewContent(family = StratumThemeId.Tonal, state = recommendationsEmptyState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInRecommendationsEmptyNeo() {
    CheckInPreviewContent(family = StratumThemeId.NeoBrutalist, state = recommendationsEmptyState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInSwapPickerTonal() {
    CheckInSwapPickerPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun checkInSwapPickerNeo() {
    CheckInSwapPickerPreviewContent(family = StratumThemeId.NeoBrutalist)
}

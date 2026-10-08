package com.stratum.feature.workout.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for the notes and workout-complete screens (STR-90). Thin `@PreviewTest`
 * wrappers over the shared preview content — fake state lives in the main-source previews
 * so Studio `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun workoutManageNotesScreenTonal() {
    ManageNotesScreenPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutManageNotesScreenNeo() {
    ManageNotesScreenPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutCompleteScreenTonal() {
    WorkoutCompleteBasePreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutCompleteScreenNeo() {
    WorkoutCompleteBasePreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutCompleteProposalsTonal() {
    WorkoutCompleteProposalsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun workoutCompleteProposalsNeo() {
    WorkoutCompleteProposalsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

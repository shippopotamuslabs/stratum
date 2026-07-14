package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.onboarding.GenerateState
import com.stratum.feature.onboarding.ImportProgramState
import com.stratum.feature.onboarding.OnboardingPathUi
import com.stratum.feature.onboarding.ReviewDraftUi

@PreviewLightDark
@Composable
private fun ReviewScreenTonalPreview() {
    ReviewScreenPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ReviewScreenNeoPreview() {
    ReviewScreenPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@Composable
private fun ReviewScreenPreviewGrid(family: StratumThemeId) {
    // Two states back-to-back: a fully-answered Generate summary and the Import summary.
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReviewScreen(
                draft = completedReviewDraft(),
                path = OnboardingPathUi.Generate,
                onGenerate = {},
                onImportExisting = {},
                generateState = GenerateState.Idle,
                generateProgress = null,
                generateMaxTimelineIndex = 0,
                generateErrorMessage = null,
                importProgramState = ImportProgramState.Idle,
                importMaxTimelineIndex = 0,
            )
            ReviewScreen(
                draft = ReviewDraftUi(),
                path = OnboardingPathUi.Import,
                onGenerate = {},
                onImportExisting = {},
                generateState = GenerateState.Idle,
                generateProgress = null,
                generateMaxTimelineIndex = 0,
                generateErrorMessage = null,
                importProgramState = ImportProgramState.Idle,
                importMaxTimelineIndex = 0,
            )
        }
    }
}

private fun completedReviewDraft(): ReviewDraftUi = ReviewDraftUi(
    trainingAge = "4–7 years",
    lastTrained = "Within the last month",
    sessionMinutes = 60,
    specialization = listOf("Quads", "Chest"),
    presetLabel = "Commercial gym",
    enabledLiftCount = 18,
)

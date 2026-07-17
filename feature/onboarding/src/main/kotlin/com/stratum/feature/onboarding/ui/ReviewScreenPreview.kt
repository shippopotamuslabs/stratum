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

// ── Happy-path summaries (Generate + Import idle) ───────────────────────────

@PreviewLightDark
@Composable
private fun ReviewScreenTonalPreview() {
    ReviewScreenSummaryGrid(family = StratumThemeId.Tonal)
}

@PreviewLightDark
@Composable
private fun ReviewScreenNeoPreview() {
    ReviewScreenSummaryGrid(family = StratumThemeId.NeoBrutalist)
}

// ── Import failure recovery (focused error pane, no top Import CTA) ─────────

@PreviewLightDark
@Composable
private fun ReviewImportRejectedTonalPreview() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState = ImportProgramState.Rejected(null),
    )
}

@PreviewLightDark
@Composable
private fun ReviewImportRejectedNeoPreview() {
    ReviewImportFailurePreview(
        family = StratumThemeId.NeoBrutalist,
        importProgramState = ImportProgramState.Rejected(null),
    )
}

@PreviewLightDark
@Composable
private fun ReviewImportErrorTonalPreview() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState = ImportProgramState.Error(null),
    )
}

@PreviewLightDark
@Composable
private fun ReviewImportErrorNeoPreview() {
    ReviewImportFailurePreview(
        family = StratumThemeId.NeoBrutalist,
        importProgramState = ImportProgramState.Error(null),
    )
}

@PreviewLightDark
@Composable
private fun ReviewImportTooLargeTonalPreview() {
    ReviewImportFailurePreview(
        family = StratumThemeId.Tonal,
        importProgramState = ImportProgramState.Error(
            message = "That file is too large. Program files should be under 1 MB.",
        ),
    )
}

@Composable
private fun ReviewScreenSummaryGrid(family: StratumThemeId) {
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
                draft = importReviewDraft(),
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

@Composable
private fun ReviewImportFailurePreview(
    family: StratumThemeId,
    importProgramState: ImportProgramState,
) {
    StratumTheme(themeId = family) {
        ReviewScreen(
            draft = importReviewDraft(),
            path = OnboardingPathUi.Import,
            onGenerate = {},
            onImportExisting = {},
            generateState = GenerateState.Idle,
            generateProgress = null,
            generateMaxTimelineIndex = 0,
            generateErrorMessage = null,
            importProgramState = importProgramState,
            importMaxTimelineIndex = 0,
        )
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

private fun importReviewDraft(): ReviewDraftUi = ReviewDraftUi(
    trainingAge = "4–7 years",
    lastTrained = "Within the last month",
    presetLabel = "Commercial gym",
    enabledLiftCount = 18,
)

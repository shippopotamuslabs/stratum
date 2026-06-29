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
import com.stratum.feature.onboarding.ProgramGenerationProgressPhaseUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.activeTimelineIndex

@PreviewLightDark
@Composable
private fun GenerateScreenLoadingTonalPreview() {
    GenerateScreenPreviewGrid(
        family = StratumThemeId.Tonal,
        phase = ProgramGenerationProgressPhaseUi.SplitPlanning,
    )
}

@PreviewLightDark
@Composable
private fun GenerateScreenLoadingNeoPreview() {
    GenerateScreenPreviewGrid(
        family = StratumThemeId.NeoBrutalist,
        phase = ProgramGenerationProgressPhaseUi.BuildingWorkouts,
    )
}

@Composable
private fun GenerateScreenPreviewGrid(
    family: StratumThemeId,
    phase: ProgramGenerationProgressPhaseUi,
) {
    StratumTheme(themeId = family) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GenerateLoadingContent(
                progress = ProgramGenerationProgressUi(phase = phase),
                maxActiveIndex = phase.activeTimelineIndex(),
            )
            GenerateLoadingContent(
                progress = ProgramGenerationProgressUi(phase = ProgramGenerationProgressPhaseUi.Complete),
                maxActiveIndex = ProgramGenerationProgressPhaseUi.Complete.activeTimelineIndex(),
            )
        }
    }
}

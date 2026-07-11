package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.ProgramGenerationLoadingContent
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ImportProgramPhaseUi
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R

/**
 * Loading timelines rendered inside ReviewScreen's Crossfade when generate or import is in flight.
 * No vertical scroll — they live inside ReviewScreen's scrolling column. Nesting two
 * verticalScrolls crashes with infinite-height constraints.
 */
@Composable
internal fun GenerateLoadingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    ProgramGenerationLoadingContent(
        progress = progress,
        maxActiveIndex = maxActiveIndex,
        modifier = modifier,
    )
}

@Composable
internal fun ImportLoadingContent(
    phase: ImportProgramPhaseUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_import_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        ProgramImportTimeline(
            phase = phase,
            maxActiveIndex = maxActiveIndex,
        )
    }
}

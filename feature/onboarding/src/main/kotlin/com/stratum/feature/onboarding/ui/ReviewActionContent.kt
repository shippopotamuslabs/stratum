package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.R

/**
 * The generation timeline, rendered inside ReviewScreen's Crossfade when generation is loading.
 * No vertical scroll — it lives inside ReviewScreen's scrolling column. Nesting two verticalScrolls
 * crashes with infinite-height constraints.
 */
@Composable
internal fun GenerateLoadingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StratumText(
            text = stringResource(R.string.onboarding_generate_loading),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        ProgramGenerationTimeline(
            progress = progress,
            maxActiveIndex = maxActiveIndex,
        )
    }
}

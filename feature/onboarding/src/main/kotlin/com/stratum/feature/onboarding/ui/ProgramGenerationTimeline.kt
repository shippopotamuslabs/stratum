package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stratum.core.ui.component.ProgramGenerationTimeline as CoreProgramGenerationTimeline
import com.stratum.feature.onboarding.ProgramGenerationProgressUi

/** Onboarding wrapper around the shared :core:ui generation timeline. */
@Composable
internal fun ProgramGenerationTimeline(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    CoreProgramGenerationTimeline(
        progress = progress,
        maxActiveIndex = maxActiveIndex,
        modifier = modifier,
    )
}

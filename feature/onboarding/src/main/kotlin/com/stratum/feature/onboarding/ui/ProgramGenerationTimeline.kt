package com.stratum.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.activeTimelineIndex
import com.stratum.feature.onboarding.programGenerationTimelinePhases

/**
 * Generation-specific wrapper around [OnboardingProgressTimeline]. Resolves phase string resources
 * and active detail for the on-device / cloud generation pipeline.
 */
@Composable
internal fun ProgramGenerationTimeline(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    val currentIndex = progress?.phase.activeTimelineIndex() ?: 0
    val activeIndex = maxOf(maxActiveIndex, currentIndex)
    val stepLabels =
        programGenerationTimelinePhases.map { phase ->
            stringResource(programGenerationPhaseStringRes(phase))
        }
    val activePhaseLabel =
        progress?.let {
            stringResource(programGenerationPhaseStringRes(it.phase))
        }
    val activeDetail =
        progress?.let { item ->
            activePhaseLabel?.let { label ->
                programGenerationProgressSubtitle(item, label)
            }
        }

    OnboardingProgressTimeline(
        stepLabels = stepLabels,
        activeIndex = activeIndex,
        activeDetail = activeDetail,
        modifier = modifier,
    )
}

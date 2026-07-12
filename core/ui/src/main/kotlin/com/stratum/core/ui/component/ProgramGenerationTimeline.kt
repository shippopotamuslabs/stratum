package com.stratum.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.progress.ProgramGenerationProgressUi
import com.stratum.core.ui.progress.activeTimelineIndex
import com.stratum.core.ui.progress.programGenerationPhaseStringRes
import com.stratum.core.ui.progress.programGenerationProgressSubtitle
import com.stratum.core.ui.progress.programGenerationTimelinePhases
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Generation-specific wrapper around [StratumProgressTimeline]. Resolves phase string resources
 * and active detail for the program generation pipeline.
 */
@Composable
fun ProgramGenerationTimeline(
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

    StratumProgressTimeline(
        stepLabels = stepLabels,
        activeIndex = activeIndex,
        activeDetail = activeDetail,
        modifier = modifier,
    )
}

/**
 * Title + generation timeline used by onboarding Review and Create Program while generation runs.
 */
@Composable
fun ProgramGenerationLoadingContent(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StratumText(
                text = stringResource(R.string.program_generate_loading),
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = stringResource(R.string.program_generate_loading_duration),
                muted = true,
            )
        }
        ProgramGenerationTimeline(
            progress = progress,
            maxActiveIndex = maxActiveIndex,
        )
    }
}

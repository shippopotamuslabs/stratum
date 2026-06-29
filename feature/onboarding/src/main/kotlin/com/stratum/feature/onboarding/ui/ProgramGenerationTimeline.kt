package com.stratum.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.onboarding.ProgramGenerationProgressUi
import com.stratum.feature.onboarding.ProgramGenerationTimelineStepStatus
import com.stratum.feature.onboarding.R as FeatureR
import com.stratum.feature.onboarding.activeTimelineIndex
import com.stratum.feature.onboarding.programGenerationTimelinePhases
import com.stratum.feature.onboarding.timelineStepStatus

private val TimelineDotSize = 10.dp
private val TimelineGutterWidth = 28.dp
private val TimelineLineWidth = 2.dp
private val StepRowMinHeight = 52.dp
private val StatusIndicatorSize = 24.dp

@Composable
internal fun ProgramGenerationTimeline(
    progress: ProgramGenerationProgressUi?,
    maxActiveIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val currentIndex = progress?.phase.activeTimelineIndex() ?: 0
    val activeIndex = maxOf(maxActiveIndex, currentIndex)
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

    Column(modifier = modifier.fillMaxWidth()) {
        programGenerationTimelinePhases.forEachIndexed { index, phase ->
            val status = timelineStepStatus(index, activeIndex)
            val isFirst = index == 0
            val isLast = index == programGenerationTimelinePhases.lastIndex
            val lineAboveActive = index > 0 && index <= activeIndex
            val lineBelowActive = index < activeIndex
            val phaseLabel = stringResource(programGenerationPhaseStringRes(phase))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = StepRowMinHeight)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(TimelineGutterWidth)
                        .fillMaxHeight()
                        .drawBehind {
                            val centerX = size.width / 2f
                            val dotCenterY = StepRowMinHeight.toPx() / 2f
                            if (!isFirst) {
                                drawLine(
                                    color =
                                        if (lineAboveActive) {
                                            colors.textPrimary
                                        } else {
                                            colors.borderSubtle
                                        },
                                    start = Offset(centerX, 0f),
                                    end = Offset(centerX, dotCenterY),
                                    strokeWidth = TimelineLineWidth.toPx(),
                                )
                            }
                            if (!isLast) {
                                drawLine(
                                    color =
                                        if (lineBelowActive) {
                                            colors.textPrimary
                                        } else {
                                            colors.borderSubtle
                                        },
                                    start = Offset(centerX, dotCenterY),
                                    end = Offset(centerX, size.height),
                                    strokeWidth = TimelineLineWidth.toPx(),
                                )
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier.height(StepRowMinHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        TimelineDot(status = status)
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = StepRowMinHeight),
                    verticalArrangement = Arrangement.Center,
                ) {
                    StratumText(
                        text = phaseLabel,
                        modifier =
                            if (status == ProgramGenerationTimelineStepStatus.Pending) {
                                Modifier.alpha(0.55f)
                            } else {
                                Modifier
                            },
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = status != ProgramGenerationTimelineStepStatus.Completed,
                    )
                    if (status == ProgramGenerationTimelineStepStatus.Active) {
                        activeDetail?.let { detail ->
                            StratumText(
                                text = detail,
                                muted = true,
                                style = StratumThemeAccessor.typography.sectionLabel,
                            )
                        }
                    }
                }
                TimelineStepStatusIndicator(status = status)
            }
        }
    }
}

@Composable
private fun TimelineDot(status: ProgramGenerationTimelineStepStatus) {
    val colors = StratumThemeAccessor.colors
    val dotColor =
        when (status) {
            ProgramGenerationTimelineStepStatus.Completed,
            ProgramGenerationTimelineStepStatus.Active,
            -> colors.textPrimary
            ProgramGenerationTimelineStepStatus.Pending -> colors.borderSubtle
        }
    Box(
        modifier = Modifier
            .size(TimelineDotSize)
            .background(color = dotColor, shape = CircleShape),
    )
}

@Composable
private fun TimelineStepStatusIndicator(status: ProgramGenerationTimelineStepStatus) {
    val colors = StratumThemeAccessor.colors
    when (status) {
        ProgramGenerationTimelineStepStatus.Completed ->
            Box(
                modifier = Modifier
                    .size(StatusIndicatorSize)
                    .background(color = colors.textPrimary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(FeatureR.string.onboarding_generate_timeline_checkmark),
                    style = StratumThemeAccessor.typography.sectionLabel.copy(color = colors.surfaceBase),
                )
            }
        ProgramGenerationTimelineStepStatus.Active ->
            Box(
                modifier = Modifier
                    .size(StatusIndicatorSize)
                    .border(width = 2.dp, color = colors.borderSubtle, shape = CircleShape),
            )
        ProgramGenerationTimelineStepStatus.Pending ->
            Spacer(modifier = Modifier.size(StatusIndicatorSize))
    }
}

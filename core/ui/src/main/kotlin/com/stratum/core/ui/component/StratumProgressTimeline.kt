package com.stratum.core.ui.component

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
import com.stratum.core.ui.R
import com.stratum.core.ui.progress.ProgressTimelineStepStatus
import com.stratum.core.ui.progress.timelineStepStatus
import com.stratum.core.ui.theme.StratumThemeAccessor

private val TimelineDotSize = 10.dp
private val TimelineGutterWidth = 28.dp
private val TimelineLineWidth = 2.dp
private val StepRowMinHeight = 52.dp
private val StatusIndicatorSize = 24.dp

/**
 * Shared vertical status timeline (program generation, import, etc.).
 * [stepLabels] are already resolved strings; [activeIndex] is the furthest step in progress
 * (monotonic via ViewModel max-index tracking).
 */
@Composable
fun StratumProgressTimeline(
    stepLabels: List<String>,
    activeIndex: Int,
    activeDetail: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    Column(modifier = modifier.fillMaxWidth()) {
        stepLabels.forEachIndexed { index, phaseLabel ->
            val status = timelineStepStatus(index, activeIndex)
            val isFirst = index == 0
            val isLast = index == stepLabels.lastIndex
            val lineAboveActive = index > 0 && index <= activeIndex
            val lineBelowActive = index < activeIndex

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
                            if (status == ProgressTimelineStepStatus.Pending) {
                                Modifier.alpha(0.55f)
                            } else {
                                Modifier
                            },
                        style = StratumThemeAccessor.typography.bodyLarge,
                        muted = status != ProgressTimelineStepStatus.Completed,
                    )
                    if (status == ProgressTimelineStepStatus.Active) {
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
private fun TimelineDot(status: ProgressTimelineStepStatus) {
    val colors = StratumThemeAccessor.colors
    val dotColor =
        when (status) {
            ProgressTimelineStepStatus.Completed,
            ProgressTimelineStepStatus.Active,
            -> colors.textPrimary
            ProgressTimelineStepStatus.Pending -> colors.borderSubtle
        }
    Box(
        modifier = Modifier
            .size(TimelineDotSize)
            .background(color = dotColor, shape = CircleShape),
    )
}

@Composable
private fun TimelineStepStatusIndicator(status: ProgressTimelineStepStatus) {
    val colors = StratumThemeAccessor.colors
    when (status) {
        ProgressTimelineStepStatus.Completed ->
            Box(
                modifier = Modifier
                    .size(StatusIndicatorSize)
                    .background(color = colors.textPrimary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(R.string.program_generate_timeline_checkmark),
                    style = StratumThemeAccessor.typography.sectionLabel.copy(color = colors.surfaceBase),
                )
            }
        ProgressTimelineStepStatus.Active ->
            Box(
                modifier = Modifier
                    .size(StatusIndicatorSize)
                    .border(width = 2.dp, color = colors.borderSubtle, shape = CircleShape),
            )
        ProgressTimelineStepStatus.Pending ->
            Spacer(modifier = Modifier.size(StatusIndicatorSize))
    }
}

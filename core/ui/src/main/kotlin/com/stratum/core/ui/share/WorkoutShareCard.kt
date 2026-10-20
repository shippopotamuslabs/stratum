// STR-142 allowlist: exported-bitmap card geometry (20.dp margin, 2.dp micro-gap) pinned; inner gaps adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum.core.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R
import com.stratum.core.ui.component.StratumBrandFooter
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Instagram-square workout summary card. Layout-only — capture lives in
 * [com.stratum.core.ui.share.ShareCaptureHost].
 */
@Composable
fun WorkoutShareCard(
    content: WorkoutShareContent,
    modifier: Modifier = Modifier,
) {
    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderColor = StratumThemeAccessor.colors.borderStrong
    val surface = StratumThemeAccessor.colors.surfaceBase
    val raised = StratumThemeAccessor.colors.surfaceRaised

    Column(
        modifier =
            modifier
                .background(color = surface, shape = panelShape)
                .border(width = borderWidth, color = borderColor, shape = panelShape)
                .padding(horizontal = 20.dp, vertical = StratumSpacing.s16),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4)) {
                StratumText(
                    text = stringResource(R.string.workout_share_title),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumText(
                    text = content.workoutName,
                    style =
                        StratumThemeAccessor.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ShareStatRow(content = content)

            if (content.visibleSets.isNotEmpty()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .background(color = raised, shape = panelShape)
                            .border(
                                width = StratumThemeAccessor.shapes.borderWidth,
                                color = StratumThemeAccessor.colors.borderSubtle,
                                shape = panelShape,
                            )
                            .padding(horizontal = StratumSpacing.s12, vertical = StratumSpacing.s8),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    content.visibleSets.forEachIndexed { index, set ->
                        ShareLiftRow(set = set)
                        if (index < content.visibleSets.lastIndex) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = StratumSpacing.s8)
                                        .height(StratumThemeAccessor.shapes.borderWidth)
                                        .background(StratumThemeAccessor.colors.borderSubtle),
                            )
                        }
                    }
                    if (content.overflowCount > 0) {
                        Spacer(modifier = Modifier.height(StratumSpacing.s8))
                        StratumText(
                            text =
                                stringResource(
                                    R.string.workout_share_more_lifts,
                                    content.overflowCount,
                                ),
                            style = StratumThemeAccessor.typography.sectionLabel,
                            muted = true,
                        )
                    }
                }
            } else {
                StratumText(
                    text = stringResource(R.string.workout_share_empty),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
            }
        }

        Spacer(modifier = Modifier.height(StratumSpacing.s12))
        StratumBrandFooter()
    }
}

@Composable
private fun ShareStatRow(content: WorkoutShareContent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        ShareStatChip(
            label = stringResource(R.string.workout_share_duration),
            value = content.durationFormatted,
            modifier = Modifier.weight(1f),
        )
        ShareStatChip(
            label = stringResource(R.string.workout_share_personal_records),
            value = content.personalRecordCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ShareStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val shape = StratumThemeAccessor.shapes.panel
    Column(
        modifier =
            modifier
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = shape,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = shape,
                )
                .padding(horizontal = StratumSpacing.s12, vertical = StratumSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StratumText(
            text = label,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        StratumNumber(
            text = value,
            style = StratumThemeAccessor.typography.dataLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShareLiftRow(set: ShareLiftRowUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        if (set.isPersonalRecord) {
            Icon(
                painter = painterResource(R.drawable.ic_personal_record),
                contentDescription = stringResource(R.string.workout_share_pr_cd),
                tint = StratumThemeAccessor.colors.prHighlight,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(StratumSpacing.s16))
        }
        StratumText(
            text = set.liftName,
            style =
                StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StratumNumber(
            text = set.performanceLabel,
            style = StratumThemeAccessor.typography.dataLarge,
            maxLines = 1,
        )
    }
}

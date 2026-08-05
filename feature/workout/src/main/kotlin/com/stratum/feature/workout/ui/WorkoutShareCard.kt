package com.stratum.feature.workout.ui

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
import com.stratum.core.ui.component.StratumBrandFooter
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.workout.R
import com.stratum.core.ui.R as CoreUiR

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
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StratumText(
                    text = stringResource(R.string.workout_complete_title),
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    content.visibleSets.forEachIndexed { index, set ->
                        ShareLiftRow(set = set)
                        if (index < content.visibleSets.lastIndex) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .height(StratumThemeAccessor.shapes.borderWidth)
                                        .background(StratumThemeAccessor.colors.borderSubtle),
                            )
                        }
                    }
                    if (content.overflowCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StratumText(
                            text =
                                stringResource(
                                    R.string.workout_complete_share_more_lifts,
                                    content.overflowCount,
                                ),
                            style = StratumThemeAccessor.typography.sectionLabel,
                            muted = true,
                        )
                    }
                }
            } else {
                StratumText(
                    text = stringResource(R.string.workout_complete_empty),
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        StratumBrandFooter()
    }
}

@Composable
private fun ShareStatRow(content: WorkoutShareContent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ShareStatChip(
            label = stringResource(R.string.workout_complete_duration),
            value = content.durationFormatted,
            modifier = Modifier.weight(1f),
        )
        ShareStatChip(
            label = stringResource(R.string.workout_complete_personal_records),
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (set.isPersonalRecord) {
            Icon(
                painter = painterResource(CoreUiR.drawable.ic_personal_record),
                contentDescription = stringResource(R.string.workout_complete_pr_cd),
                tint = StratumThemeAccessor.colors.prHighlight,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
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

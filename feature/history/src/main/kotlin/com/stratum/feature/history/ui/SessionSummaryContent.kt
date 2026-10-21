package com.stratum.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumMuscleVolumeCardState
import com.stratum.core.ui.component.StratumMuscleVolumeRow
import com.stratum.core.ui.component.StratumStatCard
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumVolumeBodyTier
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R

/**
 * Shared header block used by session detail and multi-workout day summary cards:
 * workout name, completed time, duration / sets / PRs, and muscle volume row.
 */
@Composable
internal fun SessionSummaryContent(
    workoutName: String,
    completedAtLabel: String,
    durationFormatted: String,
    totalSetsLabel: String,
    personalRecordsLabel: String,
    upperVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    lowerVolumeByMuscle: List<StratumMuscleVolumeCardState>,
    onEditTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s16),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4)) {
            StratumText(
                text = workoutName,
                style = StratumThemeAccessor.typography.titleLarge,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            ) {
                StratumText(
                    text = completedAtLabel,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    muted = true,
                )
                StratumIconButton(
                    onClick = onEditTimeClick,
                    containerColor = Color.Transparent,
                ) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_edit),
                        contentDescription = stringResource(R.string.history_edit_workout_time),
                        tint = StratumThemeAccessor.colors.textMuted,
                    )
                }
            }
        }

        SessionStatsRow(
            durationFormatted = durationFormatted,
            totalSetsLabel = totalSetsLabel,
            personalRecordsLabel = personalRecordsLabel,
        )

        if (upperVolumeByMuscle.isNotEmpty() || lowerVolumeByMuscle.isNotEmpty()) {
            HorizontalDivider(color = StratumThemeAccessor.colors.borderSubtle)
            val initialTier =
                if (upperVolumeByMuscle.isEmpty() && lowerVolumeByMuscle.isNotEmpty()) {
                    StratumVolumeBodyTier.Lower
                } else {
                    StratumVolumeBodyTier.Upper
                }
            StratumMuscleVolumeRow(
                upperCards = upperVolumeByMuscle,
                lowerCards = lowerVolumeByMuscle,
                initialTier = initialTier,
            )
        }
    }
}

@Composable
internal fun SessionStatsRow(
    durationFormatted: String,
    totalSetsLabel: String,
    personalRecordsLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumStatCard(
            label = stringResource(R.string.history_session_duration),
            value = durationFormatted,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textMuted,
                )
            },
            contentPadding = StratumSpacing.s12,
            valueStyle = StratumThemeAccessor.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        StratumStatCard(
            label = stringResource(R.string.history_session_sets),
            value = totalSetsLabel,
            leading = {
                Icon(
                    imageVector = Icons.Outlined.Tag,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textMuted,
                )
            },
            contentPadding = StratumSpacing.s12,
            valueStyle = StratumThemeAccessor.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        StratumStatCard(
            label = stringResource(R.string.history_session_prs),
            value = personalRecordsLabel,
            leading = {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_personal_record),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.prHighlight,
                )
            },
            contentPadding = StratumSpacing.s12,
            valueStyle = StratumThemeAccessor.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumStatCard
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.R as CoreUiR
import com.stratum.feature.workout.R

@Composable
fun WorkoutCompleteScreen(
    state: WorkoutCompleteUiState.Loaded,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = state.workoutName,
                actions = {
                    StratumTextButton(
                        text = stringResource(R.string.workout_complete_close),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StratumText(
                text = stringResource(R.string.workout_complete_title),
                style = StratumThemeAccessor.typography.titleLarge,
            )

            SummaryStatCards(state)

            if (state.bestSets.isNotEmpty()) {
                PerformanceTable(state.bestSets)
            }
        }
    }
}

@Composable
private fun SummaryStatCards(state: WorkoutCompleteUiState.Loaded) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StratumStatCard(
            label = stringResource(R.string.workout_complete_duration),
            value = state.durationFormatted,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.textMuted,
                )
            },
            modifier = Modifier.weight(1f),
        )
        val prCount = state.bestSets.count { it.isPersonalRecord }
        StratumStatCard(
            label = stringResource(R.string.workout_complete_personal_records),
            value = prCount.toString(),
            leading = {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_personal_record),
                    contentDescription = null,
                    tint = StratumThemeAccessor.colors.prHighlight,
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PerformanceTable(bestSets: List<LiftBestSetUi>) {
    val panelShape = StratumThemeAccessor.shapes.panel
    val borderWidth = StratumThemeAccessor.shapes.borderWidth
    val borderColor = StratumThemeAccessor.colors.borderSubtle
    val cardColor = StratumThemeAccessor.colors.surfaceRaised

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = cardColor, shape = panelShape)
            .border(width = borderWidth, color = borderColor, shape = panelShape)
    ) {
        bestSets.forEachIndexed { index, bestSet ->
            PerformanceItem(
                bestSet = bestSet,
                modifier = Modifier.fillMaxWidth()
            )

            if (index < bestSets.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(borderWidth)
                        .background(color = borderColor)
                )
            }
        }
    }
}

@Composable
private fun PerformanceItem(
    bestSet: LiftBestSetUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Lift name and PR Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (bestSet.isPersonalRecord) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_personal_record),
                    contentDescription = stringResource(R.string.workout_complete_pr_cd),
                    tint = StratumThemeAccessor.colors.prHighlight,
                    modifier = Modifier.size(18.dp),
                )
            }
            StratumText(
                text = bestSet.liftName,
                style = StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // 3-Column Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val labelStyle = StratumThemeAccessor.typography.sectionLabel
            val valueStyle = StratumThemeAccessor.typography.dataLarge

            // Performance Column
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_performance),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.performanceLabel,
                    style = valueStyle,
                )
            }

            // RIR Column
            Column(
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_rir),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.rirLabel,
                    style = valueStyle,
                )
            }

            // Est. 1RM Column
            Column(
                modifier = Modifier.weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.workout_complete_table_est_1rm),
                    style = labelStyle,
                    muted = true,
                )
                StratumNumber(
                    text = bestSet.estimatedOneRmLabel,
                    style = valueStyle,
                )
            }
        }
    }
}

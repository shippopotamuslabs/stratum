package com.stratum.core.ui.component.workoutlog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun StratumWorkoutLogTableHeader(
    showWeightColumn: Boolean,
    weightHeaderLabel: String,
    showTrailingColumn: Boolean,
    modifier: Modifier = Modifier,
) {
    val weights = workoutLogColumnWeights(showWeightColumn, showTrailingColumn)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("Set", Modifier.weight(weights.label), alignment = Alignment.CenterStart)
        HeaderCell("Prev", Modifier.weight(weights.prev))
        if (showWeightColumn) {
            HeaderCell(weightHeaderLabel, Modifier.weight(weights.weight))
        }
        HeaderCell("Reps", Modifier.weight(weights.reps))
        HeaderCell("RIR", Modifier.weight(weights.rir))
        if (showTrailingColumn) {
            HeaderCell("", Modifier.weight(weights.trailing))
        }
    }
}

@Composable
private fun HeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = modifier,
        contentAlignment = alignment,
    ) {
        StratumText(
            text = text,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
    }
}

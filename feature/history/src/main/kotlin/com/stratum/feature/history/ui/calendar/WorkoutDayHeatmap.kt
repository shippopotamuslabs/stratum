package com.stratum.feature.history.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.theme.StratumThemeAccessor

/** Shared 7-column day heatmap used by Year and Multi-year calendar modes. */
@Composable
internal fun WorkoutDayHeatmap(
    leadingEmptyDays: Int,
    dayCount: Int,
    workoutDays: Set<Int>,
    cellHeight: Dp,
    cellGap: Dp,
    modifier: Modifier = Modifier,
) {
    val cells = buildList {
        repeat(leadingEmptyDays) { add(null as Int?) }
        for (day in 1..dayCount) add(day)
        val trailing = (7 - (size % 7)) % 7
        repeat(trailing) { add(null) }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(cellGap),
    ) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                week.forEach { day ->
                    val active = day != null && day in workoutDays
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    when {
                                        day == null -> StratumThemeAccessor.colors.surfaceBase
                                        active -> StratumThemeAccessor.colors.accent
                                        else -> StratumThemeAccessor.colors.surfaceRaised
                                    },
                                ),
                    )
                }
            }
        }
    }
}

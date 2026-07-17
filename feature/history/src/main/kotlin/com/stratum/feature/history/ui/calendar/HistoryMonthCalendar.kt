package com.stratum.feature.history.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R
import com.stratum.feature.history.ui.HistoryDayUi
import com.stratum.feature.history.ui.HistoryMonthUi
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun HistoryMonthBlock(
    month: HistoryMonthUi,
    onDayClick: (day: HistoryDayUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text =
                month.yearMonth.month
                    .getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                    " ${month.yearMonth.year}",
            style = StratumThemeAccessor.typography.titleLarge,
        )
        WeekdayHeaderRow()
        MonthDayGrid(month = month, onDayClick = onDayClick)
    }
}

@Composable
internal fun WeekdayHeaderRow(modifier: Modifier = Modifier) {
    val labels =
        listOf(
            stringResource(R.string.history_weekday_sun),
            stringResource(R.string.history_weekday_mon),
            stringResource(R.string.history_weekday_tue),
            stringResource(R.string.history_weekday_wed),
            stringResource(R.string.history_weekday_thu),
            stringResource(R.string.history_weekday_fri),
            stringResource(R.string.history_weekday_sat),
        )
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEach { label ->
            StratumText(
                text = label,
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthDayGrid(
    month: HistoryMonthUi,
    onDayClick: (day: HistoryDayUi) -> Unit,
) {
    val cells = buildList {
        repeat(month.leadingEmptyDays) { add(null) }
        addAll(month.days)
        val trailing = (7 - (size % 7)) % 7
        repeat(trailing) { add(null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (day != null) {
                            HistoryDayCell(day = day, onDayClick = onDayClick)
                        } else {
                            Spacer(modifier = Modifier.fillMaxWidth().height(56.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDayCell(
    day: HistoryDayUi,
    onDayClick: (day: HistoryDayUi) -> Unit,
) {
    val hasSessions = day.sessionIds.isNotEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (hasSessions) {
                        Modifier.clickable { onDayClick(day) }
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (day.hasWorkout) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.surfaceBase
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = StratumThemeAccessor.typography.bodyLarge,
                color =
                    if (day.hasWorkout) {
                        StratumThemeAccessor.colors.onAccent
                    } else {
                        StratumThemeAccessor.colors.textMuted
                    },
            )
        }
        StratumText(
            text = day.workoutLabel.orEmpty(),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

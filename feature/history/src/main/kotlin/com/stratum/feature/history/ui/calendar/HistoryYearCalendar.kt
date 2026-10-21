package com.stratum.feature.history.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.ui.HistoryMiniMonthUi
import com.stratum.feature.history.ui.HistoryYearUi
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
internal fun HistoryYearBlock(
    year: HistoryYearUi,
    onMonthClick: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
    ) {
        StratumText(
            text = year.year.toString(),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        year.months.chunked(3).forEach { rowMonths ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            ) {
                rowMonths.forEach { mini ->
                    MiniMonthHeatmap(
                        mini = mini,
                        onClick = {
                            if (mini.isNavigable) onMonthClick(mini.yearMonth)
                        },
                        interactive = mini.isNavigable,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowMonths.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun MiniMonthHeatmap(
    mini: HistoryMiniMonthUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    // Observable read: recomposes if the system locale changes.
    // platformLocale bridges Compose's Locale to java.util.Locale for getDisplayName.
    val locale = LocalLocale.current.platformLocale
    Column(
        modifier =
            modifier.then(
                if (interactive) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        StratumText(
            text = mini.yearMonth.month.getDisplayName(TextStyle.SHORT, locale),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        WorkoutDayHeatmap(
            leadingEmptyDays = mini.leadingEmptyDays,
            dayCount = mini.dayCount,
            workoutDays = mini.workoutDays,
            cellHeight = 8.dp,
            cellGap = 2.dp,
        )
    }
}

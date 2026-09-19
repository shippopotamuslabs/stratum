package com.stratum.feature.history.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.ui.HistoryYearUi
import java.time.format.TextStyle

@Composable
internal fun HistoryMultiYearBlock(
    year: HistoryYearUi,
    onYearClick: (year: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Observable read: recomposes if the system locale changes.
    // platformLocale bridges Compose's Locale to java.util.Locale for getDisplayName.
    val locale = LocalLocale.current.platformLocale
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onYearClick(year.year) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = year.year.toString(),
            style = StratumThemeAccessor.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            year.months.forEach { mini ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StratumText(
                        text =
                            mini.yearMonth.month
                                .getDisplayName(TextStyle.SHORT, locale)
                                .take(1),
                        style = StratumThemeAccessor.typography.sectionLabel,
                        muted = true,
                    )
                    WorkoutDayHeatmap(
                        leadingEmptyDays = mini.leadingEmptyDays,
                        dayCount = mini.dayCount,
                        workoutDays = mini.workoutDays,
                        cellHeight = 4.dp,
                        cellGap = 1.dp,
                    )
                }
            }
        }
    }
}

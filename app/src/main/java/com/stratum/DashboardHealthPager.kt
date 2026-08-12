package com.stratum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTrendChart
import com.stratum.core.ui.component.StratumVolumeBarChart
import com.stratum.core.ui.theme.StratumThemeAccessor

private val PagerHeight = 240.dp
private val StageBarHeight = 8.dp
private val DotSize = 6.dp
private val HealthChromeSpacing = 12.dp

/**
 * Dashboard shell: write-only users keep board tabs in the top bar; Health Connect reads add a
 * titled top bar, pager, then the same tabs above the board.
 */
@Composable
internal fun DashboardHealthScaffold(
    health: DashboardHealthUiState,
    settingsAction: @Composable RowScope.() -> Unit,
    boardTabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    board: @Composable ColumnScope.() -> Unit,
) {
    StratumTabScaffold(
        modifier = modifier,
        topBar = {
            if (health.visible) {
                StratumTopBar(
                    title = stringResource(R.string.dashboard_title),
                    actions = settingsAction,
                )
            } else {
                StratumTopBar(
                    titleContent = boardTabs,
                    actions = settingsAction,
                )
            }
        },
    ) {
        if (health.visible) {
            DashboardHealthPager(
                state = health,
                modifier = Modifier.padding(bottom = HealthChromeSpacing),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = HealthChromeSpacing),
                contentAlignment = Alignment.Center,
            ) {
                boardTabs()
            }
        }
        board()
    }
}

@Composable
internal fun DashboardHealthPager(
    state: DashboardHealthUiState,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedPage,
        pageCount = { HEALTH_PAGER_PAGE_COUNT },
    )
    val panelShape = StratumThemeAccessor.shapes.panel
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PagerHeight)
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = panelShape,
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = panelShape,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> ActivityHealthPage(state.activity)
                1 -> WeightHealthPage(state.weight)
                else -> RecoveryHealthPage(state.recovery)
            }
        }
        PageDots(
            selectedIndex = pagerState.currentPage,
            count = HEALTH_PAGER_PAGE_COUNT,
        )
    }
}

@Composable
private fun ActivityHealthPage(page: ActivityHealthPageUi) {
    HealthPageColumn(
        title = stringResource(R.string.dashboard_health_activity),
        hero = page.todayStepsText,
        empty = stringResource(R.string.dashboard_health_steps_empty),
        secondary = page.sevenDayAverageText?.let {
            stringResource(R.string.dashboard_health_steps_average, it)
        },
    ) {
        StratumVolumeBarChart(
            series = page.dailyPoints,
            dayLabels = page.dayLabels,
            contentDescription = stringResource(R.string.dashboard_health_steps_chart_cd),
        )
    }
}

@Composable
private fun WeightHealthPage(page: WeightHealthPageUi) {
    val secondary = buildString {
        page.sevenDayMeanText?.let {
            append(stringResource(R.string.dashboard_health_weight_average, it))
        }
        page.deltaText?.let { delta ->
            if (isNotEmpty()) append("  ")
            append(delta)
        }
    }.ifEmpty { null }
    HealthPageColumn(
        title = stringResource(R.string.dashboard_health_weight),
        hero = page.latestText,
        empty = stringResource(R.string.dashboard_health_weight_empty),
        secondary = listOfNotNull(page.latestDateText, secondary).joinToString(" · ").ifEmpty { null },
    ) {
        StratumTrendChart(
            primarySeries = page.trendPoints,
            primaryColor = StratumThemeAccessor.colors.textData,
            contentDescription = stringResource(R.string.dashboard_health_weight_chart_cd),
        )
    }
}

@Composable
private fun RecoveryHealthPage(page: RecoveryHealthPageUi) {
    HealthPageColumn(
        title = stringResource(R.string.dashboard_health_recovery),
        hero = page.lastNightDurationText,
        empty = stringResource(R.string.dashboard_health_sleep_empty),
        secondary = recoverySecondary(page),
    ) {
        page.zones?.let { SleepStageBar(it) }
        StratumVolumeBarChart(
            series = page.nightlyPoints,
            dayLabels = page.dayLabels,
            contentDescription = stringResource(R.string.dashboard_health_sleep_chart_cd),
        )
    }
}

@Composable
private fun recoverySecondary(page: RecoveryHealthPageUi): String? {
    val parts = buildList {
        page.restingHrText?.let { current ->
            val average = page.restingHrAverageText
            add(
                if (average != null) {
                    stringResource(R.string.dashboard_health_rhr_with_average, current, average)
                } else {
                    stringResource(R.string.dashboard_health_rhr, current)
                },
            )
        }
        page.hrvText?.let {
            add(stringResource(R.string.dashboard_health_hrv, it))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ")
}

@Composable
private fun HealthPageColumn(
    title: String,
    hero: String?,
    empty: String,
    secondary: String?,
    extra: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StratumText(
            text = title,
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
        )
        if (hero != null) {
            StratumNumber(text = hero)
        } else {
            StratumText(text = empty, muted = true)
        }
        if (secondary != null) {
            StratumText(
                text = secondary,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
        extra()
    }
}

@Composable
private fun SleepStageBar(zones: SleepZonesUi) {
    val colors = StratumThemeAccessor.colors
    val description = stringResource(R.string.dashboard_health_sleep_zones_cd)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(StageBarHeight)
            .semantics { contentDescription = description },
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        var x = 0f
        val segments = listOf(
            zones.lightFraction to colors.textMuted,
            zones.deepFraction to colors.accent,
            zones.remFraction to colors.textData,
            zones.awakeFraction to colors.borderSubtle,
        )
        segments.forEach { (fraction, color) ->
            val width = size.width * fraction
            if (width > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
                x += width
            }
        }
    }
}

@Composable
private fun PageDots(selectedIndex: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(DotSize)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            StratumThemeAccessor.colors.accent
                        } else {
                            StratumThemeAccessor.colors.borderSubtle
                        },
                    ),
            )
        }
    }
}

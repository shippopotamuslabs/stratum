package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.CompactChartValueFormat
import com.stratum.core.ui.component.DetailYScale
import com.stratum.core.ui.component.DonutSliceUi
import com.stratum.core.ui.component.StratumDonutChart
import com.stratum.core.ui.component.StratumEdgeToEdgeCard
import com.stratum.core.ui.component.StratumMultiLineChart
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumPullToRefresh
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumVolumeBarChart
import com.stratum.core.ui.theme.StratumThemeAccessor

private val PagerHeight = 268.dp
private val EmptyPagerHeight = 108.dp
private val DotSize = 6.dp
private val LegendDotSize = 6.dp
private val DONUT_SIZE = 112.dp
private const val DONUT_COLUMN_WEIGHT = 0.42f
private const val STAGE_CHART_COLUMN_WEIGHT = 0.58f
private const val PAGER_SNAP_THRESHOLD = 0.14f
private const val AWAKE_BLEND_TOWARD_SURFACE = 0.28f
internal val HealthChromeSpacing = 12.dp

/**
 * Dashboard shell: Muscles | 7-Day | Lifts stay in the top bar. Health Connect, when visible,
 * is a full-bleed header inside the board list so it scrolls away with the charts. The top bar
 * uses the same raised surface as the pager so they read as one slab.
 */
@Composable
internal fun DashboardHealthScaffold(
    healthVisible: Boolean,
    settingsAction: @Composable RowScope.() -> Unit,
    boardTabs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    board: @Composable ColumnScope.() -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    StratumTabScaffold(
        modifier = modifier,
        horizontalPadding = 0.dp,
        topBar = {
            StratumTopBar(
                titleContent = boardTabs,
                actions = settingsAction,
                containerColor = if (healthVisible) colors.surfaceRaised else colors.surfaceBase,
            )
        },
    ) {
        if (onRefresh != null) {
            StratumPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    content = board,
                )
            }
        } else {
            board()
        }
    }
}

@Composable
internal fun DashboardHealthPager(
    state: DashboardHealthUiState,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val hasChart = state.hasChart()
    StratumEdgeToEdgeCard(
        modifier = modifier.height(if (hasChart) PagerHeight else EmptyPagerHeight),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        // Merge with the raised top bar — no Neo iron band at the seam.
        drawEdgeBands = false,
    ) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = PAGER_SNAP_THRESHOLD,
            ),
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> ActivityHealthPage(state.activity, expandChart = hasChart)
                1 -> WeightHealthPage(state.weight, expandChart = hasChart)
                else -> RecoveryHealthPage(state.recovery, expandChart = hasChart)
            }
        }
        PageDots(
            selectedIndex = pagerState.currentPage,
            count = HEALTH_PAGER_PAGE_COUNT,
        )
    }
}

@Composable
private fun ActivityHealthPage(
    page: ActivityHealthPageUi,
    expandChart: Boolean,
) {
    HealthPageColumn(
        title = stringResource(R.string.dashboard_health_activity),
        hero = page.todayStepsText,
        empty = stringResource(R.string.dashboard_health_steps_empty),
        secondary = page.sevenDayAverageText?.let {
            stringResource(R.string.dashboard_health_steps_average, it)
        },
        fillHeight = expandChart,
    ) {
        if (page.hasChart()) {
            StratumVolumeBarChart(
                series = page.dailyPoints,
                dayLabels = page.dayLabels,
                contentDescription = stringResource(R.string.dashboard_health_steps_chart_cd),
                modifier = healthChartModifier(expandChart),
                fillHeight = expandChart,
            )
        }
    }
}

@Composable
private fun WeightHealthPage(
    page: WeightHealthPageUi,
    expandChart: Boolean,
) {
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
        fillHeight = expandChart,
    ) {
        if (page.hasChart()) {
            StratumMultiLineChart(
                primarySeries = page.trendPoints,
                primaryColor = StratumThemeAccessor.colors.textData,
                dayLabels = page.dayLabels,
                yScale = DetailYScale.Weight,
                contentDescription = stringResource(R.string.dashboard_health_weight_chart_cd),
                modifier = healthChartModifier(expandChart),
            )
        }
    }
}

@Composable
private fun RecoveryHealthPage(
    page: RecoveryHealthPageUi,
    expandChart: Boolean,
) {
    val hasStages = page.stageSlices.isNotEmpty()
    Column(
        modifier = if (expandChart) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(R.string.dashboard_health_recovery),
                style = StratumThemeAccessor.typography.sectionLabel,
                muted = true,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            RecoveryVitalsRow(page)
        }
        if (!hasStages) {
            val hero = page.lastNightDurationText
            if (hero != null) {
                StratumNumber(text = hero)
            } else {
                StratumText(
                    text = stringResource(R.string.dashboard_health_sleep_empty),
                    muted = true,
                )
            }
        }
        if (hasStages) {
            RecoveryStagesRow(
                page = page,
                fillHeight = expandChart,
            )
        } else if (page.hasChart()) {
            StratumVolumeBarChart(
                series = page.nightlyPoints,
                dayLabels = page.dayLabels,
                contentDescription = stringResource(R.string.dashboard_health_sleep_chart_cd),
                modifier = healthChartModifier(expandChart),
                fillHeight = expandChart,
            )
        }
    }
}

@Composable
private fun ColumnScope.RecoveryStagesRow(
    page: RecoveryHealthPageUi,
    fillHeight: Boolean,
) {
    val colors = StratumThemeAccessor.colors
    Row(
        modifier = if (fillHeight) {
            Modifier
                .weight(1f)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(DONUT_COLUMN_WEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StratumDonutChart(
                slices = page.stageSlices.map { slice ->
                    DonutSliceUi(
                        fraction = slice.fraction,
                        color = slice.stage.color(),
                    )
                },
                contentDescription = stringResource(R.string.dashboard_health_sleep_zones_cd),
                modifier = Modifier.size(DONUT_SIZE),
            ) {
                page.lastNightDurationText?.let { duration ->
                    StratumNumber(
                        text = duration,
                        style = StratumThemeAccessor.typography.titleMedium,
                    )
                }
            }
            SleepStageLegend(slices = page.stageSlices)
        }
        StratumMultiLineChart(
            primarySeries = page.lightNightlyPoints,
            secondarySeries = page.deepNightlyPoints,
            tertiarySeries = page.remNightlyPoints,
            quaternarySeries = page.awakeNightlyPoints,
            primaryColor = colors.textMuted,
            secondaryColor = colors.accent,
            tertiaryColor = colors.textData,
            quaternaryColor = awakeStageColor(),
            dayLabels = page.dayLabels,
            valueFormat = CompactChartValueFormat.HoursMinutes,
            contentDescription = stringResource(R.string.dashboard_health_sleep_stages_chart_cd),
            modifier = Modifier
                .weight(STAGE_CHART_COLUMN_WEIGHT)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun SleepStageLegend(slices: List<SleepStageSliceUi>) {
    val typography = StratumThemeAccessor.typography
    val durationStyle = typography.dataLarge.copy(
        fontSize = typography.sectionLabel.fontSize,
        lineHeight = typography.sectionLabel.lineHeight,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        slices.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { slice ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(LegendDotSize)
                                .clip(CircleShape)
                                .background(slice.stage.color()),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            StratumText(
                                text = stringResource(slice.stage.labelRes()),
                                muted = true,
                                style = typography.sectionLabel,
                                maxLines = 1,
                            )
                            StratumNumber(
                                text = slice.durationText,
                                style = durationStyle,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SleepStageUi.color(): Color {
    val colors = StratumThemeAccessor.colors
    return when (this) {
        SleepStageUi.Light -> colors.textMuted
        SleepStageUi.Deep -> colors.accent
        SleepStageUi.Rem -> colors.textData
        SleepStageUi.Awake -> awakeStageColor()
    }
}

@Composable
private fun awakeStageColor(): Color {
    val colors = StratumThemeAccessor.colors
    return lerp(colors.error, colors.surfaceRaised, AWAKE_BLEND_TOWARD_SURFACE)
}

private fun SleepStageUi.labelRes(): Int = when (this) {
    SleepStageUi.Light -> R.string.dashboard_health_sleep_stage_light
    SleepStageUi.Deep -> R.string.dashboard_health_sleep_stage_deep
    SleepStageUi.Rem -> R.string.dashboard_health_sleep_stage_rem
    SleepStageUi.Awake -> R.string.dashboard_health_sleep_stage_awake
}

@Composable
private fun RecoveryVitalsRow(page: RecoveryHealthPageUi) {
    val description = recoverySecondary(page) ?: return
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        page.restingHrText?.let { current ->
            RecoveryVitalChip(
                label = stringResource(R.string.dashboard_health_vital_rhr),
                value = current,
                secondaryLabel = page.restingHrAverageText?.let {
                    stringResource(R.string.dashboard_health_vital_avg)
                },
                secondaryValue = page.restingHrAverageText,
            )
        }
        page.hrvText?.let { hrv ->
            RecoveryVitalChip(
                label = stringResource(R.string.dashboard_health_vital_hrv),
                value = hrv,
            )
        }
    }
}

@Composable
private fun RecoveryVitalChip(
    label: String,
    value: String,
    secondaryLabel: String? = null,
    secondaryValue: String? = null,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val valueStyle = typography.dataLarge.copy(
        fontSize = typography.sectionLabel.fontSize,
        lineHeight = typography.sectionLabel.lineHeight,
    )
    Row(
        modifier = Modifier
            .background(color = colors.surfaceSunken, shape = StratumThemeAccessor.shapes.chip)
            .clip(StratumThemeAccessor.shapes.chip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VitalChipReading(
            label = label,
            value = value,
            valueStyle = valueStyle,
            labelStyle = typography.sectionLabel,
        )
        if (secondaryLabel != null && secondaryValue != null) {
            StratumText(
                text = stringResource(R.string.dashboard_health_vital_separator),
                muted = true,
                style = typography.sectionLabel,
                maxLines = 1,
            )
            VitalChipReading(
                label = secondaryLabel,
                value = secondaryValue,
                valueStyle = valueStyle,
                labelStyle = typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun VitalChipReading(
    label: String,
    value: String,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
) {
    StratumText(
        text = label,
        muted = true,
        style = labelStyle,
        maxLines = 1,
    )
    StratumNumber(
        text = value,
        style = valueStyle,
        maxLines = 1,
    )
}

private fun ColumnScope.healthChartModifier(expandChart: Boolean): Modifier =
    if (expandChart) {
        Modifier
            .weight(1f)
            .fillMaxWidth()
    } else {
        Modifier.fillMaxWidth()
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
    fillHeight: Boolean,
    extra: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
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

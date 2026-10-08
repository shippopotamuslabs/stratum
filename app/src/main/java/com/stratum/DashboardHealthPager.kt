package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.stratum.core.ui.component.DetailYScale
import com.stratum.core.ui.component.StratumEdgeToEdgeCard
import com.stratum.core.ui.component.StratumMultiLineChart
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumPullToRefresh
import com.stratum.core.ui.component.StratumStackedBarChart
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumVolumeBarChart
import com.stratum.core.ui.component.TrendPointUi
import com.stratum.core.ui.theme.StratumThemeAccessor

private val PagerHeight = 268.dp
private val EmptyPagerHeight = 108.dp
private const val PAGER_SNAP_THRESHOLD = 0.14f
private const val AWAKE_BLEND_TOWARD_SURFACE = 0.28f
internal val HealthChromeSpacing = 12.dp

/**
 * Dashboard shell: plain "Dashboard" title in the top bar; board switching lives with the
 * cards in the Lift Metrics selector (STR-69). The Health Connect hero band — pager when
 * enabled, centered Settings prompt when disabled — is a full-bleed header inside the board
 * list so it scrolls away with the charts. The top bar uses the same raised surface as the
 * hero band so they read as one slab.
 */
@Composable
internal fun DashboardHealthScaffold(
    heroVisible: Boolean,
    settingsAction: @Composable RowScope.() -> Unit,
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
                title = stringResource(R.string.dashboard_title),
                actions = settingsAction,
                containerColor = if (heroVisible) colors.surfaceRaised else colors.surfaceBase,
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
        HealthPagerPills(pagerState = pagerState)
    }
}

// One-off pager indicator (UI_DESIGN.md §7.2 step 4): labeled pills used only here, so they
// live in this feature file instead of becoming a shared Stratum* component. Semantic colors
// only, mirroring the selected/unselected roles of StratumFilterChip.
@Composable
private fun HealthPagerPills(pagerState: PagerState) {
    val labels =
        listOf(
            stringResource(R.string.dashboard_health_activity),
            stringResource(R.string.dashboard_health_weight),
            stringResource(R.string.dashboard_health_recovery),
        )
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            HealthPagerPill(
                label = label,
                selected = index == pagerState.currentPage,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

/**
 * Hero-band replacement when Health Connect is disabled: same full-bleed shaded slab as the
 * pager ([drawEdgeBands] off so it merges with the top bar), with a centered prompt that
 * opens Settings.
 */
@Composable
internal fun DashboardHealthDisabledPrompt(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StratumEdgeToEdgeCard(
        modifier = modifier.clickable(role = Role.Button, onClick = onNavigateToSettings),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        drawEdgeBands = false,
    ) {
        StratumText(
            text = stringResource(R.string.dashboard_health_disabled_prompt),
            muted = true,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Single hero-band entry point: pager when Health Connect is enabled, Settings prompt when
 * disabled. Both the status-column and loaded-grid paths render through here so band content
 * can't drift between call sites.
 */
@Composable
internal fun DashboardHeroBand(
    health: DashboardHealthUiState,
    pagerState: PagerState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (health.visible) {
        DashboardHealthPager(
            state = health,
            pagerState = pagerState,
            modifier = modifier,
        )
    } else {
        DashboardHealthDisabledPrompt(
            onNavigateToSettings = onNavigateToSettings,
            modifier = modifier,
        )
    }
}

@Composable
private fun HealthPagerPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val shape = StratumThemeAccessor.shapes.chip
    StratumText(
        text = label,
        style = StratumThemeAccessor.typography.sectionLabel,
        color = if (selected) colors.onAccent else colors.textMuted,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surfaceSunken)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = if (selected) colors.accent else colors.borderSubtle,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
                yScale = DetailYScale.Steps,
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
                showYAxis = true,
            )
        }
    }
}

/**
 * STR-98 unified recovery page: one layout for both staged and stage-less sleep.
 * Three tappable stat cards (Sleep / RHR / HRV) select the 7-day trend shown in the
 * viewport below. The sleep chart stacks bars by stage whenever any night has stage
 * data (plain bars otherwise); the legend is a color key only — per-night stage
 * durations come from tapping a bar (Vico marker).
 */
@Composable
private fun RecoveryHealthPage(
    page: RecoveryHealthPageUi,
    expandChart: Boolean,
) {
    var selection by remember { mutableStateOf(RecoveryTrendSelection.Sleep) }
    Column(
        modifier = if (expandChart) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StratumText(
            text = stringResource(R.string.dashboard_health_recovery),
            style = StratumThemeAccessor.typography.sectionLabel,
            muted = true,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecoveryStatCard(
                label = stringResource(R.string.dashboard_health_stat_sleep),
                value = page.lastNightDurationText,
                secondary = page.nightlyAverageText?.let {
                    stringResource(R.string.dashboard_health_stat_average, it)
                },
                selected = selection == RecoveryTrendSelection.Sleep,
                onClick = { selection = RecoveryTrendSelection.Sleep },
                modifier = Modifier.weight(1f),
            )
            RecoveryStatCard(
                label = stringResource(R.string.dashboard_health_vital_rhr),
                value = page.restingHrText,
                secondary = page.restingHrAverageText?.let {
                    stringResource(R.string.dashboard_health_stat_average, it)
                },
                selected = selection == RecoveryTrendSelection.RestingHr,
                onClick = { selection = RecoveryTrendSelection.RestingHr },
                modifier = Modifier.weight(1f),
            )
            RecoveryStatCard(
                label = stringResource(R.string.dashboard_health_vital_hrv),
                value = page.hrvText,
                secondary = page.hrvAverageText?.let {
                    stringResource(R.string.dashboard_health_stat_average, it)
                },
                selected = selection == RecoveryTrendSelection.Hrv,
                onClick = { selection = RecoveryTrendSelection.Hrv },
                modifier = Modifier.weight(1f),
            )
        }
        when (selection) {
            RecoveryTrendSelection.Sleep -> RecoverySleepTrend(
                page = page,
                expandChart = expandChart,
            )
            RecoveryTrendSelection.RestingHr -> RecoveryVitalTrend(
                points = page.restingHrDailyPoints,
                dayLabels = page.dayLabels,
                contentDescription = stringResource(R.string.dashboard_health_rhr_trend_chart_cd),
                emptyText = stringResource(R.string.dashboard_health_rhr_empty),
                expandChart = expandChart,
            )
            RecoveryTrendSelection.Hrv -> RecoveryVitalTrend(
                points = page.hrvDailyPoints,
                dayLabels = page.dayLabels,
                contentDescription = stringResource(R.string.dashboard_health_hrv_trend_chart_cd),
                emptyText = stringResource(R.string.dashboard_health_hrv_empty),
                expandChart = expandChart,
            )
        }
    }
}

private enum class RecoveryTrendSelection {
    Sleep,
    RestingHr,
    Hrv,
}

// One-off selectable stat card (UI_DESIGN.md §7.2 step 4): used only on the recovery page,
// mirroring the selected/unselected roles of HealthPagerPill. Semantic colors only.
@Composable
private fun RecoveryStatCard(
    label: String,
    value: String?,
    secondary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val shape = StratumThemeAccessor.shapes.panel
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = if (selected) colors.accent else colors.borderSubtle,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StratumText(
            text = label,
            style = typography.sectionLabel,
            muted = true,
            maxLines = 1,
        )
        StratumNumber(
            text = value ?: "—",
            style = typography.titleLarge,
            maxLines = 1,
        )
        if (secondary != null) {
            StratumText(
                text = secondary,
                style = typography.sectionLabel,
                muted = true,
                maxLines = 1,
            )
        }
    }
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
private fun ColumnScope.RecoverySleepTrend(
    page: RecoveryHealthPageUi,
    expandChart: Boolean,
) {
    val hasStages = page.lightNightlyPoints.any { it.value > 0f } ||
        page.deepNightlyPoints.any { it.value > 0f } ||
        page.remNightlyPoints.any { it.value > 0f } ||
        page.awakeNightlyPoints.any { it.value > 0f }
    if (hasStages) {
        StratumStackedBarChart(
            series = listOf(
                page.lightNightlyPoints,
                page.deepNightlyPoints,
                page.remNightlyPoints,
                page.awakeNightlyPoints,
            ),
            segmentColors = listOf(
                SleepStageUi.Light.color(),
                SleepStageUi.Deep.color(),
                SleepStageUi.Rem.color(),
                SleepStageUi.Awake.color(),
            ),
            contentDescription = stringResource(R.string.dashboard_health_sleep_chart_cd),
            dayLabels = page.dayLabels,
            modifier = healthChartModifier(expandChart),
            fillHeight = expandChart,
            // Volume scale: 0-based range with plain-number labels — the right shape for hours.
            yScale = DetailYScale.Volume,
        )
        SleepStageColorKey()
    } else if (page.nightlyPoints.any { it.value > 0f }) {
        StratumVolumeBarChart(
            series = page.nightlyPoints,
            dayLabels = page.dayLabels,
            contentDescription = stringResource(R.string.dashboard_health_sleep_chart_cd),
            modifier = healthChartModifier(expandChart),
            fillHeight = expandChart,
            yScale = DetailYScale.Volume,
        )
    } else if (page.showSleepEmptyText()) {
        StratumText(
            text = stringResource(R.string.dashboard_health_sleep_empty),
            muted = true,
        )
    }
}

/** Color key for the stacked sleep bars: stage names only, no durations (see STR-98). */
@Composable
private fun SleepStageColorKey() {
    val stages = listOf(
        SleepStageUi.Light,
        SleepStageUi.Deep,
        SleepStageUi.Rem,
        SleepStageUi.Awake,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stages.forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(stage.color()),
                )
                Spacer(modifier = Modifier.width(4.dp))
                StratumText(
                    text = stringResource(stage.labelRes()),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RecoveryVitalTrend(
    points: List<TrendPointUi>,
    dayLabels: List<String>,
    contentDescription: String,
    emptyText: String,
    expandChart: Boolean,
) {
    if (points.size >= 2) {
        StratumMultiLineChart(
            primarySeries = points,
            contentDescription = contentDescription,
            dayLabels = dayLabels,
            modifier = healthChartModifier(expandChart),
        )
    } else {
        StratumText(
            text = emptyText,
            muted = true,
        )
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



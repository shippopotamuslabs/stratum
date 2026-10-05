package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumIndexVolumeChart
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTrendChart
import com.stratum.core.ui.component.StratumVolumeBarChart
import com.stratum.core.ui.theme.StratumThemeAccessor

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    // A workout completed in another tab refreshes the weekly tables on finalize; reload on resume
    // so the board never shows a stale snapshot when the user returns to the dashboard.
    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose { }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val health by viewModel.healthUiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val graphDetail by viewModel.graphDetailState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        selectedTab = selectedTab,
        health = health,
        isRefreshing = isRefreshing,
        onRefresh = if (health.visible) viewModel::refresh else null,
        onSelectedTabChange = viewModel::selectTab,
        onNavigateToSettings = onNavigateToSettings,
        onOpenLiftGraph = viewModel::openLiftGraph,
        onOpenMuscleGraph = viewModel::openMuscleGraph,
        onOpenMuscleDailyVolumeGraph = viewModel::openMuscleDailyVolumeGraph,
        onRetry = viewModel::retry,
        modifier = modifier,
    )

    DashboardGraphDetailHost(
        state = graphDetail,
        onDismiss = viewModel::dismissGraphDetail,
        onSelectMode = viewModel::selectGraphMode,
        onSelectRange = viewModel::selectGraphTimeRange,
        onRetry = viewModel::retryGraphDetail,
    )
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: DashboardBoardTabUi = DashboardBoardTabUi.Muscles,
    health: DashboardHealthUiState = DashboardHealthUiState(),
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onSelectedTabChange: (DashboardBoardTabUi) -> Unit = {},
    onOpenLiftGraph: (String) -> Unit = {},
    onOpenMuscleGraph: (String) -> Unit = {},
    onOpenMuscleDailyVolumeGraph: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val boardTabLabels = listOf(
        stringResource(R.string.dashboard_tab_muscles),
        stringResource(R.string.dashboard_tab_seven_day),
        stringResource(R.string.dashboard_tab_lifts),
    )
    val healthPagerState = rememberPagerState(
        initialPage = health.selectedPage.coerceIn(0, HEALTH_PAGER_PAGE_COUNT - 1),
        pageCount = { HEALTH_PAGER_PAGE_COUNT },
    )
    val settingsAction: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        StratumIconButton(onClick = onNavigateToSettings) {
            Icon(
                painter = painterResource(UiR.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_title),
                tint = StratumThemeAccessor.colors.textPrimary,
            )
        }
    }
    // The hero band shows whenever there is board content to anchor it: the pager when
    // Health Connect is enabled, the Settings prompt when disabled. Error has no band.
    val heroVisible = health.visible || state != DashboardUiState.Error
    DashboardHealthScaffold(
        heroVisible = heroVisible,
        settingsAction = settingsAction,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        when (state) {
            DashboardUiState.Loading -> LoadingState(
                health = health,
                pagerState = healthPagerState,
                onNavigateToSettings = onNavigateToSettings,
            )
            DashboardUiState.Empty -> EmptyState(
                health = health,
                pagerState = healthPagerState,
                onNavigateToSettings = onNavigateToSettings,
            )
            DashboardUiState.Error -> StratumFeatureErrorFallback(onRetry = onRetry)
            is DashboardUiState.Loaded -> LoadedContent(
                state = state,
                tab = selectedTab,
                health = health,
                pagerState = healthPagerState,
                boardTabLabels = boardTabLabels,
                onSelectedTabChange = onSelectedTabChange,
                onNavigateToSettings = onNavigateToSettings,
                onOpenLiftGraph = onOpenLiftGraph,
                onOpenMuscleGraph = onOpenMuscleGraph,
                onOpenMuscleDailyVolumeGraph = onOpenMuscleDailyVolumeGraph,
            )
        }
    }
}

@Composable
private fun ColumnScope.LoadingState(
    health: DashboardHealthUiState,
    pagerState: PagerState,
    onNavigateToSettings: () -> Unit,
) {
    DashboardStatusColumn(
        health = health,
        pagerState = pagerState,
        onNavigateToSettings = onNavigateToSettings,
    ) {
        StratumText(
            text = stringResource(R.string.dashboard_loading),
            muted = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = BOARD_HORIZONTAL_PADDING_DP.dp),
        )
    }
}

@Composable
private fun ColumnScope.EmptyState(
    health: DashboardHealthUiState,
    pagerState: PagerState,
    onNavigateToSettings: () -> Unit,
) {
    DashboardStatusColumn(
        health = health,
        pagerState = pagerState,
        onNavigateToSettings = onNavigateToSettings,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StratumText(
                text = stringResource(R.string.dashboard_empty_title),
                style = StratumThemeAccessor.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            StratumText(
                text = stringResource(R.string.dashboard_empty_subtitle),
                muted = true,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ColumnScope.DashboardStatusColumn(
    health: DashboardHealthUiState,
    pagerState: PagerState,
    onNavigateToSettings: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        if (health.visible) {
            DashboardHealthPager(
                state = health,
                pagerState = pagerState,
                modifier = Modifier.padding(bottom = HealthChromeSpacing),
            )
        } else {
            DashboardHealthDisabledPrompt(
                onNavigateToSettings = onNavigateToSettings,
                modifier = Modifier.padding(bottom = HealthChromeSpacing),
            )
        }
        content()
    }
}

@Composable
private fun ColumnScope.LoadedContent(
    state: DashboardUiState.Loaded,
    tab: DashboardBoardTabUi,
    health: DashboardHealthUiState,
    pagerState: PagerState,
    boardTabLabels: List<String>,
    onSelectedTabChange: (DashboardBoardTabUi) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenLiftGraph: (String) -> Unit,
    onOpenMuscleGraph: (String) -> Unit,
    onOpenMuscleDailyVolumeGraph: (String) -> Unit,
) {
    val hasItems = when (tab) {
        DashboardBoardTabUi.Muscles -> state.muscles.isNotEmpty()
        DashboardBoardTabUi.SevenDay -> state.runningDailyVolume.isNotEmpty()
        DashboardBoardTabUi.Lifts -> state.liftSections.any { it.lifts.isNotEmpty() }
    }
    val showStatusLegend = when (tab) {
        DashboardBoardTabUi.Muscles -> state.muscles.any { it.highlight != null }
        DashboardBoardTabUi.Lifts ->
            state.liftSections.any { section -> section.lifts.any { it.highlight != null } }
        DashboardBoardTabUi.SevenDay -> false
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(CARDS_PER_ROW),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        contentPadding = PaddingValues(
            start = BOARD_HORIZONTAL_PADDING_DP.dp,
            end = BOARD_HORIZONTAL_PADDING_DP.dp,
            bottom = 16.dp,
        ),
    ) {
        if (health.visible) {
            item(
                key = "health-pager",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DashboardHealthPager(
                    state = health,
                    pagerState = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .breakoutHorizontal(BOARD_HORIZONTAL_PADDING_DP.dp),
                )
            }
        } else {
            item(
                key = "health-disabled-prompt",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DashboardHealthDisabledPrompt(
                    onNavigateToSettings = onNavigateToSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .breakoutHorizontal(BOARD_HORIZONTAL_PADDING_DP.dp),
                )
            }
        }
        // Board switch glued to the cards it drives. Always rendered while loaded so the
        // selector stays usable even when the selected tab is empty.
        item(
            key = "lift-metrics-selector",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StratumText(
                    text = stringResource(R.string.dashboard_lift_metrics_label),
                    style = StratumThemeAccessor.typography.sectionLabel,
                    muted = true,
                )
                StratumSegmentedControl(
                    segments = boardTabLabels,
                    selectedIndex = tab.index,
                    onSelectedIndexChange = { onSelectedTabChange(DashboardBoardTabUi.fromIndex(it)) },
                )
            }
        }
        if (hasItems) {
            item(
                key = "legend",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                BoardLegend(
                    tab = tab,
                    windowWeeks = state.windowWeeks,
                    showStatusLegend = showStatusLegend,
                )
            }
        }
        when (tab) {
            DashboardBoardTabUi.Muscles -> {
                if (state.muscles.isEmpty()) {
                    emptyTabItem(R.string.dashboard_empty_muscles_window)
                } else {
                    items(
                        items = state.muscles,
                        key = { muscle -> "muscle-${muscle.name}" },
                    ) { muscle ->
                        MuscleTrendCard(
                            muscle = muscle,
                            onClick = { onOpenMuscleGraph(muscle.muscleKey) },
                        )
                    }
                }
            }
            DashboardBoardTabUi.SevenDay -> {
                if (state.runningDailyVolume.isEmpty()) {
                    emptyTabItem(R.string.dashboard_empty_seven_day_window)
                } else {
                    items(
                        items = state.runningDailyVolume,
                        key = { muscle -> "daily-${muscle.name}" },
                    ) { muscle ->
                        SevenDayVolumeCard(
                            muscle = muscle,
                            onClick = { onOpenMuscleDailyVolumeGraph(muscle.muscleKey) },
                        )
                    }
                }
            }
            DashboardBoardTabUi.Lifts -> {
                if (state.liftSections.isEmpty()) {
                    emptyTabItem(R.string.dashboard_empty_lifts_program)
                } else {
                    liftSectionItems(
                        sections = state.liftSections,
                        onOpenLiftGraph = onOpenLiftGraph,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardLegend(
    tab: DashboardBoardTabUi,
    windowWeeks: Int,
    showStatusLegend: Boolean,
) {
    val legendModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = CARD_PADDING_DP.dp, vertical = CARD_SPACING_DP.dp / 2)
    when (tab) {
        DashboardBoardTabUi.SevenDay -> DashboardLegend(
            primaryLabel = stringResource(R.string.dashboard_legend_volume),
            primaryColor = StratumThemeAccessor.colors.accent,
            primaryStyle = LegendSwatchStyle.Bar,
            secondaryLabel = null,
            secondaryColor = Color.Unspecified,
            caption = stringResource(R.string.dashboard_legend_seven_day_caption),
            showStatusLegend = false,
            modifier = legendModifier,
        )
        DashboardBoardTabUi.Muscles -> DashboardLegend(
            primaryLabel = stringResource(R.string.dashboard_legend_progress),
            primaryColor = StratumThemeAccessor.colors.setComplete,
            primaryStyle = LegendSwatchStyle.Bar,
            secondaryLabel = stringResource(R.string.dashboard_legend_volume),
            secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA),
            caption = stringResource(R.string.dashboard_legend_baseline_caption),
            showStatusLegend = showStatusLegend,
            modifier = legendModifier,
        )
        DashboardBoardTabUi.Lifts -> DashboardLegend(
            primaryLabel = stringResource(R.string.dashboard_legend_strength),
            primaryColor = StratumThemeAccessor.colors.textData,
            primaryStyle = LegendSwatchStyle.Line,
            secondaryLabel = stringResource(R.string.dashboard_legend_volume),
            secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA),
            caption = stringResource(
                R.string.dashboard_legend_lifts_window_caption,
                windowWeeks,
            ),
            showStatusLegend = showStatusLegend,
            modifier = legendModifier,
        )
    }
}

private fun LazyGridScope.emptyTabItem(textRes: Int) {
    item(
        key = "empty-tab",
        span = { GridItemSpan(maxLineSpan) },
    ) {
        StratumText(
            text = stringResource(textRes),
            muted = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
        )
    }
}

@Composable
private fun MuscleTrendCard(
    muscle: MuscleSeriesUi,
    onClick: () -> Unit,
) {
    TrendCard(
        name = muscle.name,
        // No per-card trend arrow: high-confidence direction is the border accent (see legend dots).
        badge = null,
        headline = muscle.headlineVolume,
        // Baseline semantics live under the global legend — keep card footers to lift count only.
        footer = stringResource(R.string.dashboard_contributing_lifts_format, muscle.contributingLifts),
        highlight = muscle.highlight,
        onClick = onClick,
    ) {
        StratumIndexVolumeChart(
            indexSeries = muscle.progressPoints,
            volumeSeries = muscle.volumePoints,
            contentDescription = "${muscle.name} progress index and volume",
        )
    }
}

@Composable
private fun SevenDayVolumeCard(
    muscle: MuscleDailyVolumeUi,
    onClick: () -> Unit,
) {
    TrendCard(
        name = muscle.name,
        badge = null,
        headline = muscle.headlineSets,
        footer = stringResource(R.string.dashboard_contributing_lifts_format, muscle.contributingLifts),
        highlight = null,
        onClick = onClick,
    ) {
        StratumVolumeBarChart(
            series = muscle.volumePoints,
            dayLabels = muscle.dayLabels,
            barColor = StratumThemeAccessor.colors.accent,
            contentDescription = "${muscle.name} daily volume",
        )
    }
}

private fun LazyGridScope.liftSectionItems(
    sections: List<LiftSectionUi>,
    onOpenLiftGraph: (String) -> Unit,
) {
    sections.forEach { section ->
        item(
            key = "section-${section.title}",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            LiftSectionHeader(title = sectionTitle(section.title))
        }
        items(
            items = section.lifts,
            key = { lift -> "lift-${section.title}-${lift.name}" },
        ) { lift ->
            LiftTrendCard(
                lift = lift,
                onClick = { onOpenLiftGraph(lift.liftId) },
            )
        }
    }
}

@Composable
private fun LiftSectionHeader(title: String) {
    StratumText(
        text = title,
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SECTION_HEADER_TOP_PADDING_DP.dp, bottom = SECTION_HEADER_BOTTOM_PADDING_DP.dp),
    )
}

@Composable
private fun sectionTitle(key: String): String = when (key) {
    SECTION_UPPER -> stringResource(R.string.dashboard_section_upper)
    SECTION_LOWER -> stringResource(R.string.dashboard_section_lower)
    else -> key
}

@Composable
private fun LiftTrendCard(
    lift: LiftSeriesUi,
    onClick: () -> Unit,
) {
    TrendCard(
        name = lift.name,
        // Match muscles: no per-card slope arrow. Mild e1RM noise was rendering as a loud red
        // "down" glyph without a callout border (badge gate is weaker than highlight). Direction
        // only surfaces via high-confidence border + legend dots.
        badge = null,
        headline = lift.e1rmText ?: stringResource(R.string.dashboard_trend_unknown),
        // Window is stated once in the lifts legend caption — footer is session count only.
        footer = stringResource(R.string.dashboard_lift_footer_format, lift.sessions),
        highlight = lift.highlight,
        onClick = onClick,
    ) {
        StratumTrendChart(
            primarySeries = lift.performancePoints,
            secondarySeries = lift.volumePoints,
            primaryColor = StratumThemeAccessor.colors.textData,
            secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA),
            contentDescription = "${lift.name} performance and volume",
        )
    }
}

@Composable
private fun TrendCard(
    name: String,
    badge: TrendBadge?,
    headline: String,
    footer: String,
    highlight: CalloutDirectionUi? = null,
    onClick: (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    val badgeColor = badge?.color()
    // Progressing uses accent (primary) — setComplete is a dark mint that reads as muted on
    // raised cards; accent stays as bright as error so both callout borders scan equally well.
    val borderColor = when (highlight) {
        CalloutDirectionUi.Progressing -> StratumThemeAccessor.colors.accent
        CalloutDirectionUi.Regressing -> StratumThemeAccessor.colors.error
        null -> StratumThemeAccessor.colors.borderSubtle
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = borderColor,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .then(clickModifier)
            .padding(CARD_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(text = name, style = StratumThemeAccessor.typography.titleMedium)
            if (badge != null) TrendDot(badge = badge, color = badgeColor!!)
        }
        StratumText(text = headline, style = StratumThemeAccessor.typography.bodyLarge)
        chart()
        StratumText(text = footer, muted = true, style = StratumThemeAccessor.typography.sectionLabel)
    }
}

/**
 * A compact, color-coded trend dot. Down/regressing uses the error color so problems surface at a
 * glance; up/improving uses the set-complete accent.
 */
@Composable
private fun TrendDot(badge: TrendBadge, color: Color) {
    val trendLabel = stringResource(badge.labelRes())
    Box(
        modifier = Modifier
            .size(CARD_DOT_SIZE_DP.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(width = 1.dp, color = color.copy(alpha = 0.4f), shape = CircleShape)
            .semantics { contentDescription = trendLabel },
        contentAlignment = Alignment.Center,
    ) {
        when (badge) {
            TrendBadge.Up ->
                Icon(
                    painter = painterResource(UiR.drawable.ic_trend_up),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(CARD_DOT_ICON_SIZE_DP.dp),
                )
            TrendBadge.Down ->
                Icon(
                    painter = painterResource(UiR.drawable.ic_trend_down),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(CARD_DOT_ICON_SIZE_DP.dp),
                )
            TrendBadge.Flat ->
                Icon(
                    painter = painterResource(UiR.drawable.ic_remove),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(CARD_DOT_ICON_SIZE_DP.dp),
                )
            TrendBadge.Unknown ->
                StratumText(
                    text = stringResource(R.string.dashboard_trend_unknown),
                    muted = true,
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
        }
    }
}

@Composable
private fun TrendBadge.color(): Color = when (this) {
    TrendBadge.Up -> StratumThemeAccessor.colors.setComplete
    TrendBadge.Down -> StratumThemeAccessor.colors.error
    TrendBadge.Flat -> StratumThemeAccessor.colors.textMuted
    TrendBadge.Unknown -> StratumThemeAccessor.colors.textMuted
}

private fun TrendBadge.labelRes(): Int = when (this) {
    TrendBadge.Up -> R.string.dashboard_trend_up
    TrendBadge.Down -> R.string.dashboard_trend_down
    TrendBadge.Flat -> R.string.dashboard_trend_flat
    TrendBadge.Unknown -> R.string.dashboard_trend_unknown
}

private const val CARDS_PER_ROW = 2
private const val CARD_SPACING_DP = 12
private const val BOARD_HORIZONTAL_PADDING_DP = 12
private const val CARD_PADDING_DP = 14
private const val CARD_DOT_SIZE_DP = 24
private const val CARD_DOT_ICON_SIZE_DP = 14
private const val VOLUME_GHOST_ALPHA = 0.5f
private const val SECTION_HEADER_TOP_PADDING_DP = 4
private const val SECTION_HEADER_BOTTOM_PADDING_DP = 0

private val LEGEND_SPACING = 16.dp
private val LEGEND_SWATCH_WIDTH = 16.dp
private val LEGEND_SWATCH_HEIGHT = 2.dp
private val LEGEND_BAR_SWATCH_WIDTH = 6.dp
private val LEGEND_BAR_SWATCH_HEIGHT = 12.dp
private val LEGEND_SWATCH_GAP = 6.dp
private val LEGEND_CAPTION_GAP = 4.dp

private val LEGEND_DOT_SIZE = 8.dp

/**
 * Global legend for the dashboard trend cards. Tab-aware: Muscles use index bars vs zero + volume
 * line; 7-Day is volume bars only; Lifts use dual sparklines (strength + volume). Optional
 * [caption] for baseline / window semantics. When [showStatusLegend] is true, a second row
 * explains card border accents (high-confidence progressing / regressing only).
 *
 * [secondaryLabel] null omits the secondary swatch (7-Day volume-only legend).
 */
@Composable
internal fun DashboardLegend(
    primaryLabel: String,
    primaryColor: Color,
    secondaryLabel: String?,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    primaryStyle: LegendSwatchStyle = LegendSwatchStyle.Line,
    caption: String? = null,
    showStatusLegend: Boolean = false,
) {
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LEGEND_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatch(color = primaryColor, label = primaryLabel, style = primaryStyle)
            if (secondaryLabel != null) {
                LegendSwatch(
                    color = secondaryColor,
                    label = secondaryLabel,
                    style = LegendSwatchStyle.Line,
                )
            }
        }
        if (showStatusLegend) {
            Row(
                modifier = Modifier.padding(top = LEGEND_CAPTION_GAP),
                horizontalArrangement = Arrangement.spacedBy(LEGEND_SPACING),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendSwatch(
                    color = StratumThemeAccessor.colors.accent,
                    label = stringResource(R.string.dashboard_legend_progressing),
                    style = LegendSwatchStyle.Dot,
                )
                LegendSwatch(
                    color = StratumThemeAccessor.colors.error,
                    label = stringResource(R.string.dashboard_legend_regressing),
                    style = LegendSwatchStyle.Dot,
                )
            }
        }
        if (caption != null) {
            StratumText(
                text = caption,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
                modifier = Modifier.padding(top = LEGEND_CAPTION_GAP),
            )
        }
    }
}

internal enum class LegendSwatchStyle { Line, Bar, Dot }

@Composable
private fun LegendSwatch(
    color: Color,
    label: String,
    style: LegendSwatchStyle = LegendSwatchStyle.Line,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = when (style) {
                LegendSwatchStyle.Line ->
                    Modifier
                        .size(width = LEGEND_SWATCH_WIDTH, height = LEGEND_SWATCH_HEIGHT)
                        .clip(CircleShape)
                        .background(color)
                LegendSwatchStyle.Bar ->
                    Modifier
                        .size(width = LEGEND_BAR_SWATCH_WIDTH, height = LEGEND_BAR_SWATCH_HEIGHT)
                        .clip(StratumThemeAccessor.shapes.chip)
                        .background(color)
                LegendSwatchStyle.Dot ->
                    Modifier
                        .size(LEGEND_DOT_SIZE)
                        .clip(CircleShape)
                        .background(color)
            },
        )
        Spacer(modifier = Modifier.width(LEGEND_SWATCH_GAP))
        StratumText(text = label, muted = true, style = StratumThemeAccessor.typography.sectionLabel)
    }
}

/** Draw a full-bleed header inside a horizontally padded [LazyVerticalGrid]. */
private fun Modifier.breakoutHorizontal(padding: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = padding.roundToPx()
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = constraints.maxWidth + (extra * 2),
                maxWidth = constraints.maxWidth + (extra * 2),
            ),
        )
        layout(constraints.maxWidth, placeable.height) {
            placeable.place(-extra, 0)
        }
    }

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumIndexVolumeChart
import com.stratum.core.ui.component.StratumSegmentedControl
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTrendChart
import com.stratum.core.ui.component.StratumVolumeBarChart
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.stratumButtonHardShadow

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
    val graphDetail by viewModel.graphDetailState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        selectedTab = selectedTab,
        onSelectedTabChange = viewModel::selectTab,
        onNavigateToSettings = onNavigateToSettings,
        onOpenLiftGraph = viewModel::openLiftGraph,
        onOpenMuscleGraph = viewModel::openMuscleGraph,
        onOpenMuscleDailyVolumeGraph = viewModel::openMuscleDailyVolumeGraph,
        modifier = modifier,
    )

    DashboardGraphDetailHost(
        state = graphDetail,
        onDismiss = viewModel::dismissGraphDetail,
        onSelectMode = viewModel::selectGraphMode,
        onSelectRange = viewModel::selectGraphTimeRange,
    )
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: DashboardBoardTabUi = DashboardBoardTabUi.Muscles,
    onSelectedTabChange: (DashboardBoardTabUi) -> Unit = {},
    onOpenLiftGraph: (String) -> Unit = {},
    onOpenMuscleGraph: (String) -> Unit = {},
    onOpenMuscleDailyVolumeGraph: (String) -> Unit = {},
) {
    StratumTabScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                titleContent = {
                    DashboardTabToggle(
                        labels = listOf(
                            stringResource(R.string.dashboard_tab_muscles),
                            stringResource(R.string.dashboard_tab_seven_day),
                            stringResource(R.string.dashboard_tab_lifts),
                        ),
                        selectedIndex = selectedTab.index,
                        onSelectedIndexChange = { onSelectedTabChange(DashboardBoardTabUi.fromIndex(it)) },
                    )
                },
                actions = {
                    StratumIconButton(onClick = onNavigateToSettings) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
            )
        },
    ) {
        when (state) {
            DashboardUiState.Loading -> LoadingState()
            DashboardUiState.Empty -> EmptyState()
            is DashboardUiState.Loaded -> LoadedContent(
                state = state,
                tab = selectedTab,
                onOpenLiftGraph = onOpenLiftGraph,
                onOpenMuscleGraph = onOpenMuscleGraph,
                onOpenMuscleDailyVolumeGraph = onOpenMuscleDailyVolumeGraph,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    StratumText(
        text = stringResource(R.string.dashboard_loading),
        muted = true,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    )
}

@Composable
private fun EmptyState() {
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

@Composable
private fun ColumnScope.LoadedContent(
    state: DashboardUiState.Loaded,
    tab: DashboardBoardTabUi,
    onOpenLiftGraph: (String) -> Unit,
    onOpenMuscleGraph: (String) -> Unit,
    onOpenMuscleDailyVolumeGraph: (String) -> Unit,
) {
    val hasItems = when (tab) {
        DashboardBoardTabUi.Muscles -> state.muscles.isNotEmpty()
        DashboardBoardTabUi.SevenDay -> state.runningDailyVolume.isNotEmpty()
        DashboardBoardTabUi.Lifts -> state.liftSections.any { it.lifts.isNotEmpty() }
    }
    if (hasItems) {
        val showStatusLegend = when (tab) {
            DashboardBoardTabUi.Muscles -> state.muscles.any { it.highlight != null }
            DashboardBoardTabUi.Lifts ->
                state.liftSections.any { section -> section.lifts.any { it.highlight != null } }
            DashboardBoardTabUi.SevenDay -> false
        }
        when (tab) {
            DashboardBoardTabUi.SevenDay -> DashboardLegend(
                primaryLabel = stringResource(R.string.dashboard_legend_volume),
                primaryColor = StratumThemeAccessor.colors.accent,
                primaryStyle = LegendSwatchStyle.Bar,
                secondaryLabel = null,
                secondaryColor = Color.Unspecified,
                caption = stringResource(R.string.dashboard_legend_seven_day_caption),
                showStatusLegend = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_PADDING_DP.dp, vertical = CARD_SPACING_DP.dp / 2),
            )
            DashboardBoardTabUi.Muscles -> DashboardLegend(
                primaryLabel = stringResource(R.string.dashboard_legend_progress),
                primaryColor = StratumThemeAccessor.colors.setComplete,
                primaryStyle = LegendSwatchStyle.Bar,
                secondaryLabel = stringResource(R.string.dashboard_legend_volume),
                secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA),
                caption = stringResource(R.string.dashboard_legend_baseline_caption),
                showStatusLegend = showStatusLegend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_PADDING_DP.dp, vertical = CARD_SPACING_DP.dp / 2),
            )
            DashboardBoardTabUi.Lifts -> DashboardLegend(
                primaryLabel = stringResource(R.string.dashboard_legend_strength),
                primaryColor = StratumThemeAccessor.colors.textData,
                primaryStyle = LegendSwatchStyle.Line,
                secondaryLabel = stringResource(R.string.dashboard_legend_volume),
                secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA),
                caption = stringResource(
                    R.string.dashboard_legend_lifts_window_caption,
                    state.windowWeeks,
                ),
                showStatusLegend = showStatusLegend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CARD_PADDING_DP.dp, vertical = CARD_SPACING_DP.dp / 2),
            )
        }
    }
    when (tab) {
        DashboardBoardTabUi.Muscles -> {
            if (state.muscles.isEmpty()) {
                EmptyTabMessage(stringResource(R.string.dashboard_empty_muscles_window))
            } else {
                TrendCardGrid(items = state.muscles, key = { "muscle-${it.name}" }) { muscle ->
                    MuscleTrendCard(
                        muscle = muscle,
                        onClick = { onOpenMuscleGraph(muscle.muscleKey) },
                    )
                }
            }
        }
        DashboardBoardTabUi.SevenDay -> {
            if (state.runningDailyVolume.isEmpty()) {
                EmptyTabMessage(stringResource(R.string.dashboard_empty_seven_day_window))
            } else {
                TrendCardGrid(
                    items = state.runningDailyVolume,
                    key = { "daily-${it.name}" },
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
                EmptyTabMessage(stringResource(R.string.dashboard_empty_lifts_program))
            } else {
                LiftSectionGrid(
                    sections = state.liftSections,
                    onOpenLiftGraph = onOpenLiftGraph,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyTabMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        StratumText(text = text, muted = true, textAlign = TextAlign.Center)
    }
}

@Composable
private fun <T> ColumnScope.TrendCardGrid(
    items: List<T>,
    key: (T) -> Any,
    cardContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(CARDS_PER_ROW),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(items, key = key) { item ->
            cardContent(item)
        }
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

@Composable
private fun ColumnScope.LiftSectionGrid(
    sections: List<LiftSectionUi>,
    onOpenLiftGraph: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(CARDS_PER_ROW),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING_DP.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
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
private const val CARD_PADDING_DP = 14
private const val CARD_DOT_SIZE_DP = 24
private const val CARD_DOT_ICON_SIZE_DP = 14
private const val VOLUME_GHOST_ALPHA = 0.5f
private const val SECTION_HEADER_TOP_PADDING_DP = 4
private const val SECTION_HEADER_BOTTOM_PADDING_DP = 0

/**
 * Muscles | 7-Day | Lifts board switch in the dashboard top bar.
 *
 * - **Tonal:** large underlined text tabs (dashboard-specific chrome per UI_DESIGN.md §7.2).
 * - **Neo:** [StratumSegmentedControl] with hard-offset shadow from elevations — same control
 *   language as Settings/theme pickers; no feature branching on [com.stratum.core.ui.theme.StratumThemeId].
 */
@Composable
private fun DashboardTabToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevations = StratumThemeAccessor.elevations
    if (elevations.useHardShadow) {
        val chipShape = RoundedCornerShape(StratumThemeAccessor.shapes.chipCorner)
        StratumSegmentedControl(
            modifier =
                modifier.stratumButtonHardShadow(
                    enabled = true,
                    shape = chipShape,
                    // Top bar is tight; let shadow paint into surrounding chrome.
                    reserveSpace = false,
                ),
            segments = labels,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
        )
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TAB_TOGGLE_SPACING),
    ) {
        labels.forEachIndexed { index, label ->
            TabToggleLabel(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelectedIndexChange(index) },
            )
        }
    }
}

@Composable
private fun TabToggleLabel(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val labelColor = if (selected) colors.textPrimary else colors.textMuted
    val style = remember(selected, typography, labelColor) {
        typography.titleLarge.copy(color = labelColor)
    }
    // Measure the rendered word so the underline can be ~50% of its width, centered.
    var textWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val underlineWidth = remember(textWidthPx, density) {
        with(density) { (textWidthPx * TAB_TOGGLE_UNDERLINE_RATIO).toDp() }
    }

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(vertical = TAB_TOGGLE_LABEL_VERTICAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = label,
            style = style,
            onTextLayout = { result -> textWidthPx = result.size.width.toFloat() },
        )
        Spacer(modifier = Modifier.height(TAB_TOGGLE_UNDERLINE_GAP))
        Box(
            modifier = Modifier.width(underlineWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TAB_TOGGLE_UNDERLINE_HEIGHT)
                        .clip(CircleShape)
                        .background(colors.textPrimary),
                )
            }
        }
    }
}

private val TAB_TOGGLE_SPACING = 24.dp
private val TAB_TOGGLE_LABEL_VERTICAL_PADDING = 6.dp
private val TAB_TOGGLE_UNDERLINE_GAP = 4.dp
private val TAB_TOGGLE_UNDERLINE_HEIGHT = 5.dp
private const val TAB_TOGGLE_UNDERLINE_RATIO = 0.5f

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

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumIconButton
import com.stratum.core.ui.component.StratumTabScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTrendChart
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.theme.StratumThemeAccessor

private const val TAB_MUSCLES = 0
private const val TAB_LIFTS = 1

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

    DashboardScreen(
        state = state,
        onNavigateToSettings = onNavigateToSettings,
        modifier = modifier,
    )
}

@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_MUSCLES) }

    StratumTabScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                titleContent = {
                    DashboardTabToggle(
                        labels = listOf(
                            stringResource(R.string.dashboard_tab_muscles),
                            stringResource(R.string.dashboard_tab_lifts),
                        ),
                        selectedIndex = tab,
                        onSelectedIndexChange = { tab = it },
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
            is DashboardUiState.Loaded -> LoadedContent(state, tab)
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
private fun ColumnScope.LoadedContent(state: DashboardUiState.Loaded, tab: Int) {
    val items = if (tab == TAB_MUSCLES) state.muscles else state.lifts
    if (items.isNotEmpty()) {
        DashboardLegend(
            primaryLabel = stringResource(
                if (tab == TAB_MUSCLES) R.string.dashboard_legend_progress else R.string.dashboard_legend_strength,
            ),
            primaryColor = if (tab == TAB_MUSCLES) {
                StratumThemeAccessor.colors.setComplete
            } else {
                StratumThemeAccessor.colors.textData
            },
            secondaryLabel = stringResource(R.string.dashboard_legend_volume),
            secondaryColor = if (tab == TAB_MUSCLES) {
                StratumThemeAccessor.colors.textMuted
            } else {
                StratumThemeAccessor.colors.textMuted.copy(alpha = VOLUME_GHOST_ALPHA)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CARD_PADDING_DP.dp, vertical = CARD_SPACING_DP.dp / 2),
        )
    }
    when (tab) {
        TAB_MUSCLES -> {
            if (state.muscles.isEmpty()) {
                EmptyTabMessage(stringResource(R.string.dashboard_empty_muscles_window))
            } else {
                TrendCardGrid(items = state.muscles, key = { "muscle-${it.name}" }) { muscle ->
                    MuscleTrendCard(muscle)
                }
            }
        }
        TAB_LIFTS -> {
            if (state.lifts.isEmpty()) {
                EmptyTabMessage(stringResource(R.string.dashboard_empty_lifts_program))
            } else {
                TrendCardGrid(items = state.lifts, key = { "lift-${it.name}" }) { lift ->
                    LiftTrendCard(lift)
                }
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
private fun MuscleTrendCard(muscle: MuscleSeriesUi) {
    TrendCard(
        name = muscle.name,
        // No trend dot on muscle cards: the up/down/flat signal is derived from the progress index
        // slope and is easy to misread in aggregate (a wall of red down-arrows reads as alarm, not
        // insight). A top-of-board summary may surface trend direction more thoughtfully later.
        badge = null,
        headline = muscle.headlineVolume,
        footer = stringResource(R.string.dashboard_contributing_lifts_format, muscle.contributingLifts),
    ) {
        StratumTrendChart(
            primarySeries = muscle.progressPoints,
            secondarySeries = muscle.volumePoints,
            contentDescription = "${muscle.name} progress and volume",
        )
    }
}

@Composable
private fun LiftTrendCard(lift: LiftSeriesUi) {
    TrendCard(
        name = lift.name,
        badge = lift.badge,
        headline = lift.e1rmText ?: stringResource(R.string.dashboard_trend_unknown),
        footer = listOfNotNull(
            lift.tenureText,
            stringResource(R.string.dashboard_sessions_format, lift.sessions),
        ).joinToString(separator = STRING_LIST_SEPARATOR),
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
    chart: @Composable () -> Unit,
) {
    val badgeColor = badge?.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = StratumThemeAccessor.colors.surfaceRaised,
                shape = StratumThemeAccessor.shapes.panel,
            )
            .border(
                width = StratumThemeAccessor.shapes.borderWidth,
                color = StratumThemeAccessor.colors.borderSubtle,
                shape = StratumThemeAccessor.shapes.panel,
            )
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
private const val STRING_LIST_SEPARATOR = " · "

/**
 * Larger Muscles | Lifts tab switch living under the dashboard title. Selected label is white
 * (textPrimary) and carries a short centered underline (~50% of the word width); deselected is
 * grey (textMuted) with no underline. App-local one-off per UI_DESIGN.md §7.2 — the underline
 * indicator is dashboard-specific, so it stays out of `:core:ui` until it repeats elsewhere.
 */
@Composable
private fun DashboardTabToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
private val LEGEND_SWATCH_GAP = 6.dp

/**
 * Global legend for the dashboard trend cards. Each card renders the same two-line chart, so a
 * single legend above the grid is clearer than one per card. Tab-aware: the primary line means
 * different things per tab (Muscles = composite progress index; Lifts = peak e1RM / strength), and
 * each tab's charts use different line colors, so the swatch color must match what the cards render.
 * Volume (weekly hard-set count) is the secondary line on both tabs.
 */
@Composable
internal fun DashboardLegend(
    primaryLabel: String,
    primaryColor: Color,
    secondaryLabel: String,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(color = primaryColor, label = primaryLabel)
        LegendSwatch(color = secondaryColor, label = secondaryLabel)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = LEGEND_SWATCH_WIDTH, height = LEGEND_SWATCH_HEIGHT)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(LEGEND_SWATCH_GAP))
        StratumText(text = label, muted = true, style = StratumThemeAccessor.typography.sectionLabel)
    }
}

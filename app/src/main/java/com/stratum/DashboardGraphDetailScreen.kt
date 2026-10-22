// STR-142 allowlist: unit-label baseline nudge (2.dp) pinned; section gaps adopted.
@file:Suppress("ForbiddenRawSpacingLiteralInUi")
package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stratum.core.ui.component.StratumDetailChart
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor

/**
 * Fullscreen Hevy-style graph detail hosted as a dialog over the dashboard. [state] is presentation
 * only; chart points and Y-scales arrive pre-mapped (no domain types).
 */
@Composable
fun DashboardGraphDetailHost(
    state: GraphDetailUiState,
    onDismiss: () -> Unit,
    onSelectMode: (GraphViewMode) -> Unit,
    onSelectRange: (GraphTimeRangeUi) -> Unit,
    onRetry: () -> Unit = {},
) {
    if (state is GraphDetailUiState.Hidden) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StratumThemeAccessor.colors.surfaceBase)
                .safeDrawingPadding(),
        ) {
            when (state) {
                GraphDetailUiState.Hidden -> Unit
                GraphDetailUiState.Loading -> GraphDetailLoading(onDismiss = onDismiss)
                GraphDetailUiState.Empty -> GraphDetailEmpty(onDismiss = onDismiss)
                GraphDetailUiState.Error -> GraphDetailError(onDismiss = onDismiss, onRetry = onRetry)
                is GraphDetailUiState.Ready -> GraphDetailReady(
                    state = state,
                    onDismiss = onDismiss,
                    onSelectMode = onSelectMode,
                    onSelectRange = onSelectRange,
                )
            }
        }
    }
}

@Composable
private fun GraphDetailLoading(onDismiss: () -> Unit) {
    GraphDetailFrame(
        title = stringResource(R.string.dashboard_graph_loading_title),
        onDismiss = onDismiss,
    ) {
        StratumText(
            text = stringResource(R.string.dashboard_loading),
            muted = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StratumSpacing.s48),
        )
    }
}

@Composable
private fun GraphDetailEmpty(onDismiss: () -> Unit) {
    GraphDetailFrame(
        title = stringResource(R.string.dashboard_graph_empty_title),
        onDismiss = onDismiss,
    ) {
        StratumText(
            text = stringResource(R.string.dashboard_graph_empty_body),
            muted = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = StratumSpacing.s48, horizontal = StratumSpacing.s16),
        )
    }
}

@Composable
private fun GraphDetailError(
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    GraphDetailFrame(
        title = stringResource(R.string.dashboard_graph_error_title),
        onDismiss = onDismiss,
    ) {
        StratumFeatureErrorFallback(onRetry = onRetry)
    }
}

@Composable
private fun GraphDetailFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    StratumScaffold(
        topBar = {
            StratumTopBar(
                title = title,
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onDismiss,
                        contentDescription = stringResource(R.string.dashboard_graph_back),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}

@Composable
private fun GraphDetailReady(
    state: GraphDetailUiState.Ready,
    onDismiss: () -> Unit,
    onSelectMode: (GraphViewMode) -> Unit,
    onSelectRange: (GraphTimeRangeUi) -> Unit,
) {
    GraphDetailFrame(title = state.title, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = StratumSpacing.s16),
        ) {
            // Daily volume has a single Volume mode — hide the mode row to avoid a lone tab.
            if (state.modes.size > 1) {
                ModeTabRow(
                    modes = state.modes,
                    selected = state.selectedMode,
                    kind = state.kind,
                    onSelect = onSelectMode,
                )
                Spacer(modifier = Modifier.height(StratumSpacing.s16))
            }
            StatsRow(stats = state.stats)
            Spacer(modifier = Modifier.height(StratumSpacing.s12))
            val primaryColor = colorForRole(state.primaryColorRole)
            val secondaryColor = state.secondaryColorRole?.let { colorForRole(it) }
            StratumDetailChart(
                primarySeries = state.primaryPoints,
                secondarySeries = state.secondaryPoints,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor ?: StratumThemeAccessor.colors.textMuted,
                primaryYScale = state.primaryYScale,
                secondaryYScale = state.secondaryYScale
                    ?: com.stratum.core.ui.component.DetailYScale.Volume,
                contentDescription = stringResource(
                    R.string.dashboard_graph_chart_cd,
                    state.title,
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = StratumSpacing.s8),
            )
            TimeRangePicker(
                ranges = state.ranges,
                selected = state.selectedRange,
                onSelect = onSelectRange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = StratumSpacing.s16, top = StratumSpacing.s8),
            )
        }
    }
}

@Composable
private fun colorForRole(role: GraphSeriesColorRole) = when (role) {
    GraphSeriesColorRole.Strength -> StratumThemeAccessor.colors.textData
    GraphSeriesColorRole.Progress -> StratumThemeAccessor.colors.setComplete
    GraphSeriesColorRole.Volume -> StratumThemeAccessor.colors.textMuted.copy(alpha = 0.55f)
    GraphSeriesColorRole.DailyVolume -> StratumThemeAccessor.colors.accent
}

@Composable
private fun ModeTabRow(
    modes: List<GraphViewMode>,
    selected: GraphViewMode,
    kind: GraphDetailKindUi,
    onSelect: (GraphViewMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s24),
        modifier = Modifier.fillMaxWidth(),
    ) {
        modes.forEach { mode ->
            UnderlineTab(
                label = modeLabel(mode, kind),
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

@Composable
private fun modeLabel(mode: GraphViewMode, kind: GraphDetailKindUi): String = when (mode) {
    GraphViewMode.Primary -> when (kind) {
        GraphDetailKindUi.Lift -> stringResource(R.string.dashboard_graph_metric_e1rm)
        GraphDetailKindUi.Muscle -> stringResource(R.string.dashboard_graph_metric_progress)
        GraphDetailKindUi.MuscleDailyVolume -> stringResource(R.string.dashboard_graph_metric_volume)
    }
    GraphViewMode.Volume -> stringResource(R.string.dashboard_graph_metric_volume)
    GraphViewMode.Dual -> stringResource(R.string.dashboard_graph_metric_both)
}

@Composable
private fun UnderlineTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = StratumThemeAccessor.colors
    val typography = StratumThemeAccessor.typography
    val labelColor = if (selected) colors.textPrimary else colors.textMuted
    val style = remember(selected, typography, labelColor) {
        typography.titleMedium.copy(color = labelColor)
    }
    var textWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val underlineWidth = remember(textWidthPx, density) {
        with(density) { (textWidthPx * 0.5f).toDp() }
    }

    Column(
        modifier = Modifier
            // §12.5: tab target floors at 48dp tall (content alone is 44-47dp).
            .sizeIn(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(vertical = StratumSpacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = label,
            style = style,
            onTextLayout = { result -> textWidthPx = result.size.width.toFloat() },
        )
        Spacer(modifier = Modifier.height(StratumSpacing.s4))
        Box(
            modifier = Modifier.width(underlineWidth),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary),
                )
            }
        }
    }
}

@Composable
private fun StatsRow(stats: GraphStatsUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s32),
        verticalAlignment = Alignment.Top,
    ) {
        StatBlock(
            label = avgLabel(stats.averageLabelKey),
            value = stats.averageText,
            unit = stats.unitSuffix,
            caption = stats.dateRangeText,
        )
        StatBlock(
            label = bestLabel(stats.bestLabelKey),
            value = stats.bestText,
            unit = stats.unitSuffix,
            caption = null,
        )
    }
}

@Composable
private fun avgLabel(key: GraphStatLabelKey): String = when (key) {
    GraphStatLabelKey.E1rm -> stringResource(R.string.dashboard_graph_avg_e1rm)
    GraphStatLabelKey.Volume -> stringResource(R.string.dashboard_graph_avg_volume)
    GraphStatLabelKey.Progress -> stringResource(R.string.dashboard_graph_avg_progress)
    GraphStatLabelKey.Steps -> stringResource(R.string.dashboard_graph_avg_steps)
    GraphStatLabelKey.Vitals -> stringResource(R.string.dashboard_graph_avg_vitals)
}

@Composable
private fun bestLabel(key: GraphStatLabelKey): String = when (key) {
    GraphStatLabelKey.E1rm -> stringResource(R.string.dashboard_graph_best_e1rm)
    GraphStatLabelKey.Volume -> stringResource(R.string.dashboard_graph_peak_volume)
    GraphStatLabelKey.Progress -> stringResource(R.string.dashboard_graph_best_progress)
    GraphStatLabelKey.Steps -> stringResource(R.string.dashboard_graph_peak_steps)
    GraphStatLabelKey.Vitals -> stringResource(R.string.dashboard_graph_best_vitals)
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    unit: String,
    caption: String?,
) {
    Column {
        StratumText(
            text = label,
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            StratumText(
                text = value,
                style = StratumThemeAccessor.typography.dataLarge,
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(StratumSpacing.s4))
                StratumText(
                    text = unit,
                    muted = true,
                    style = StratumThemeAccessor.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        if (!caption.isNullOrEmpty()) {
            StratumText(
                text = caption,
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun TimeRangePicker(
    ranges: List<GraphTimeRangeUi>,
    selected: GraphTimeRangeUi,
    onSelect: (GraphTimeRangeUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = StratumThemeAccessor.colors
    val pillShape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ranges.forEach { range ->
                val isSelected = range == selected
                val background = if (isSelected) colors.textPrimary else colors.surfaceRaised
                val labelColor = if (isSelected) colors.surfaceBase else colors.textMuted
                val borderColor = if (isSelected) colors.textPrimary else colors.borderSubtle
                Box(
                    modifier = Modifier
                        // §12.5: range pills floor at 48dp in both dimensions
                        // (short labels like "1W" were ~46dp wide, 34dp tall).
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(pillShape)
                        .background(background)
                        .border(
                            width = StratumThemeAccessor.shapes.borderWidth,
                            color = borderColor,
                            shape = pillShape,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(range) },
                        )
                        .semantics { this.selected = isSelected }
                        .padding(horizontal = StratumSpacing.s12, vertical = StratumSpacing.s8),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = rangeLabel(range),
                        style = StratumThemeAccessor.typography.sectionLabel.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = labelColor,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun rangeLabel(range: GraphTimeRangeUi): String = when (range) {
    GraphTimeRangeUi.OneWeek -> stringResource(R.string.dashboard_graph_range_1w)
    GraphTimeRangeUi.OneMonth -> stringResource(R.string.dashboard_graph_range_1m)
    GraphTimeRangeUi.ThreeMonths -> stringResource(R.string.dashboard_graph_range_3m)
    GraphTimeRangeUi.SixMonths -> stringResource(R.string.dashboard_graph_range_6m)
    GraphTimeRangeUi.OneYear -> stringResource(R.string.dashboard_graph_range_1y)
    GraphTimeRangeUi.All -> stringResource(R.string.dashboard_graph_range_all)
}

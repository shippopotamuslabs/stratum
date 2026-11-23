package com.stratum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumIndexVolumeChart
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassChromePadding

/**
 * Right pane of the two-pane Dashboard (STR-331). Shows the selected muscle, or hands the
 * selected lift to [liftDetailPane]. Scroll state is hoisted by the caller so it survives a
 * posture change.
 */
@Composable
internal fun DashboardDetailPane(
    board: DashboardBoardUiState.Loaded,
    pane: DashboardPaneUiState,
    muscleListState: LazyListState,
    liftListState: LazyListState,
    onRetryMuscleDetail: () -> Unit,
    liftDetailPane: @Composable (liftId: String, listState: LazyListState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StratumThemeAccessor.colors.surfaceBase),
    ) {
        when (val selection = pane.selection) {
            DashboardPaneSelectionUi.None -> PaneMessage(stringResource(R.string.dashboard_pane_pick))
            is DashboardPaneSelectionUi.Lift -> liftDetailPane(selection.liftId, liftListState)
            is DashboardPaneSelectionUi.Muscle -> {
                val muscle = board.muscles.firstOrNull { it.muscleKey == selection.muscleKey }
                if (muscle == null) {
                    PaneMessage(stringResource(R.string.dashboard_pane_pick))
                } else {
                    MuscleDetailPane(
                        muscle = muscle,
                        detail = pane.muscleDetail,
                        listState = muscleListState,
                        onRetry = onRetryMuscleDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun MuscleDetailPane(
    muscle: MuscleSeriesUi,
    detail: MuscleDetailPaneUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
) {
    val chrome = rememberGlassChromePadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        contentPadding = PaddingValues(
            start = StratumSpacing.s16,
            end = StratumSpacing.s16,
            top = chrome.top + StratumSpacing.s12,
            bottom = chrome.bottom + StratumSpacing.s16,
        ),
    ) {
        item(key = "header") { MuscleHeader(muscle) }
        item(key = "trend") { MuscleTrendPanel(muscle) }
        // A Loaded state for another muscle is the previous selection; show Loading until
        // this muscle lands.
        val loaded = (detail as? MuscleDetailPaneUiState.Loaded)
            ?.takeIf { it.muscleKey == muscle.muscleKey }
        when {
            loaded != null -> muscleDetailItems(loaded)
            detail == MuscleDetailPaneUiState.Error -> item(key = "error") {
                StratumFeatureErrorFallback(onRetry = onRetry)
            }
            else -> item(key = "loading") {
                StratumText(
                    text = stringResource(R.string.dashboard_pane_loading),
                    muted = true,
                    modifier = Modifier.padding(vertical = StratumSpacing.s24),
                )
            }
        }
    }
}

private fun LazyListScope.muscleDetailItems(detail: MuscleDetailPaneUiState.Loaded) {
    item(key = "lifts-label") { PaneSectionLabel(stringResource(R.string.dashboard_pane_lifts)) }
    if (detail.lifts.isEmpty()) {
        item(key = "lifts-empty") {
            StratumText(text = stringResource(R.string.dashboard_pane_no_lifts), muted = true)
        }
    } else {
        item(key = "lifts") {
            PanePanel {
                detail.lifts.forEach { lift -> MuscleLiftRow(lift) }
            }
        }
    }
    if (detail.recentSessions.isNotEmpty()) {
        item(key = "sessions-label") {
            PaneSectionLabel(stringResource(R.string.dashboard_pane_recent_sessions))
        }
        items(items = detail.recentSessions, key = { "session-${it.sessionId}" }) { session ->
            RecentSessionCard(session)
        }
    }
}

@Composable
private fun MuscleHeader(muscle: MuscleSeriesUi) {
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s4)) {
        StratumText(text = muscle.name, style = StratumThemeAccessor.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumNumber(text = muscle.headlineVolume, style = StratumThemeAccessor.typography.bodyLarge)
            StratumText(
                text = stringResource(R.string.dashboard_contributing_lifts_format, muscle.contributingLifts),
                muted = true,
                style = StratumThemeAccessor.typography.sectionLabel,
            )
        }
    }
}

@Composable
private fun MuscleTrendPanel(muscle: MuscleSeriesUi) {
    val (progress, volume) = muscle.lastWeeks(MUSCLE_PANE_TREND_WEEKS)
    PanePanel {
        StratumText(
            text = stringResource(R.string.dashboard_pane_trend_caption),
            muted = true,
            style = StratumThemeAccessor.typography.sectionLabel,
        )
        StratumIndexVolumeChart(
            indexSeries = progress,
            volumeSeries = volume,
            contentDescription = "${muscle.name} progress index and volume, last 8 weeks",
            modifier = Modifier
                .fillMaxWidth()
                .height(PANE_CHART_HEIGHT),
        )
        DashboardLegend(
            primaryLabel = stringResource(R.string.dashboard_legend_progress),
            primaryColor = StratumThemeAccessor.colors.setComplete,
            primaryStyle = LegendSwatchStyle.Bar,
            secondaryLabel = stringResource(R.string.dashboard_legend_volume),
            secondaryColor = StratumThemeAccessor.colors.textMuted.copy(alpha = PANE_VOLUME_GHOST_ALPHA),
            caption = stringResource(R.string.dashboard_legend_baseline_caption),
        )
    }
}

@Composable
private fun MuscleLiftRow(lift: MuscleDetailLiftUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = StratumSpacing.s4),
        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StratumText(text = lift.name)
            if (!lift.isPrimary) {
                StratumText(
                    text = stringResource(R.string.dashboard_pane_secondary),
                    muted = true,
                    style = StratumThemeAccessor.typography.sectionLabel,
                )
            }
        }
        StratumNumber(text = lift.setsPerWeekText, style = StratumThemeAccessor.typography.bodyLarge)
        TrendDot(badge = lift.badge, color = lift.badge.color())
    }
}

@Composable
private fun RecentSessionCard(session: MuscleDetailSessionUi) {
    PanePanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(text = session.workoutLabel ?: session.dateText)
            if (session.workoutLabel != null) {
                StratumText(text = session.dateText, muted = true)
            }
        }
        session.topSets.forEach { top ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StratumText(text = top.liftName, muted = true, modifier = Modifier.weight(1f))
                StratumNumber(text = top.setText, style = StratumThemeAccessor.typography.bodyMedium)
                if (top.rir != null) {
                    RirTargetBadge(rirGoal = top.rir, onClick = {}, enabled = false, size = PANE_RIR_BADGE_SIZE)
                }
            }
        }
    }
}

@Composable
internal fun PaneSectionLabel(text: String) {
    StratumText(
        text = text,
        style = StratumThemeAccessor.typography.sectionLabel,
        muted = true,
        modifier = Modifier.padding(top = StratumSpacing.s8),
    )
}

/** Raised panel used for every block in the detail panes. */
@Composable
internal fun PanePanel(content: @Composable () -> Unit) {
    val colors = StratumThemeAccessor.colors
    val shapes = StratumThemeAccessor.shapes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.surfaceRaised, shape = shapes.panel)
            .border(width = shapes.borderWidth, color = colors.borderSubtle, shape = shapes.panel)
            .padding(StratumSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8),
    ) {
        content()
    }
}

@Composable
private fun PaneMessage(text: String) {
    val chrome = rememberGlassChromePadding()
    StratumText(
        text = text,
        muted = true,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = chrome.top + StratumSpacing.s48, start = StratumSpacing.s24, end = StratumSpacing.s24),
    )
}

private val PANE_CHART_HEIGHT = 200.dp
private val PANE_RIR_BADGE_SIZE = 20.dp
private const val PANE_VOLUME_GHOST_ALPHA = 0.5f

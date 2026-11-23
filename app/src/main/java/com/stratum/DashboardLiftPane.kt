package com.stratum

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stratum.core.ui.R as UiR
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.core.ui.theme.rememberGlassChromePadding
import com.stratum.liftconfig.HistoryPersonalRecordCard
import com.stratum.liftconfig.HistorySessionRow
import com.stratum.liftconfig.LiftInformationUiState
import com.stratum.liftconfig.LiftInformationViewModel

/**
 * Lift detail for the two-pane Dashboard (STR-331): the lift information screen's setup,
 * bests, and session history as a read-only pane. Its own keyed [LiftInformationViewModel]
 * so it never shares state with a full-screen lift info entry.
 */
@Composable
internal fun DashboardLiftPaneRoute(
    liftId: String,
    listState: LazyListState,
    viewModel: LiftInformationViewModel = hiltViewModel(key = DASHBOARD_LIFT_PANE_KEY),
) {
    LaunchedEffect(liftId) {
        viewModel.initialize(liftId)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardLiftPane(
        state = state,
        listState = listState,
        onSessionExpandedToggled = viewModel::onSessionExpandedToggled,
    )
}

@Composable
internal fun DashboardLiftPane(
    state: LiftInformationUiState,
    listState: LazyListState,
    onSessionExpandedToggled: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = rememberGlassChromePadding()
    val colors = StratumThemeAccessor.colors
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
        contentPadding = PaddingValues(
            start = StratumSpacing.s16,
            end = StratumSpacing.s16,
            top = chrome.top + StratumSpacing.s12,
            bottom = chrome.bottom + StratumSpacing.s16,
        ),
    ) {
        item(key = "title") {
            StratumText(
                text = state.liftName.ifBlank { stringResource(UiR.string.lift_info_title) },
                style = StratumThemeAccessor.typography.titleLarge,
            )
        }
        item(key = "setup") { LiftSetupPanel(state) }
        item(key = "bests") { HistoryPersonalRecordCard(personalRecord = state.personalRecord) }
        item(key = "history-label") { PaneSectionLabel(stringResource(R.string.dashboard_pane_history)) }
        if (state.historySessions.isEmpty()) {
            item(key = "history-empty") {
                StratumText(text = stringResource(UiR.string.lift_info_no_history), muted = true)
            }
        } else {
            items(items = state.historySessions, key = { "session-${it.sessionId}" }) { session ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    HistorySessionRow(
                        session = session,
                        onToggle = { onSessionExpandedToggled(session.sessionId) },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = StratumSpacing.s4)
                            .height(StratumThemeAccessor.shapes.borderWidth)
                            .background(colors.borderSubtle),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiftSetupPanel(state: LiftInformationUiState) {
    val details = state.details
    val rows = buildList {
        add(stringResource(UiR.string.lift_info_equipment) to (details.equipment ?: EMPTY_VALUE))
        if (details.showLoadType) {
            add(stringResource(UiR.string.lift_info_load_type) to details.loadTypeLabel.ifBlank { EMPTY_VALUE })
        }
        details.categoryLabel?.let { add(stringResource(UiR.string.lift_info_category) to it) }
        add(stringResource(UiR.string.lift_info_fatigue_profile) to details.fatigueLabel.ifBlank { EMPTY_VALUE })
        add(stringResource(UiR.string.lift_info_default_rest) to details.defaultRestLabel.ifBlank { EMPTY_VALUE })
        add(
            stringResource(R.string.dashboard_pane_target_muscles) to
                details.primaryMuscles.joinToString { it.displayName }.ifBlank { EMPTY_VALUE },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(StratumSpacing.s8)) {
        PaneSectionLabel(stringResource(R.string.dashboard_pane_configuration))
        PanePanel {
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s12),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StratumText(text = label, muted = true, modifier = Modifier.weight(1f))
                    StratumText(text = value)
                }
            }
        }
    }
}

private const val DASHBOARD_LIFT_PANE_KEY = "dashboard-lift-pane"
private const val EMPTY_VALUE = "—"

package com.stratum.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R

@Composable
fun HistoryDaySessionsScreen(
    state: HistoryDaySessionsUiState,
    onBackClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when (state) {
            is HistoryDaySessionsUiState.Loaded -> state.dateTitle
            else -> stringResource(R.string.history_day_sessions_title)
        }

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                title = title,
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.history_navigate_back),
                    )
                },
            )
        },
    ) { padding ->
        when (state) {
            HistoryDaySessionsUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(text = "…", muted = true)
                }
            HistoryDaySessionsUiState.Error ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(
                        text = stringResource(R.string.history_day_sessions_load_error),
                        muted = true,
                    )
                }
            is HistoryDaySessionsUiState.Loaded ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = state.sessions,
                        key = { it.sessionId },
                    ) { session ->
                        HistoryDaySessionCard(
                            session = session,
                            onClick = { onSessionClick(session.sessionId) },
                        )
                    }
                }
        }
    }
}

/**
 * Compact day-list card: name, time, stats. No interactive volume row (avoids nested click targets
 * and keeps the list scannable). Full volume lives on session detail.
 */
@Composable
private fun HistoryDaySessionCard(
    session: HistoryDaySessionSummaryUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = StratumThemeAccessor.shapes.panel
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = StratumThemeAccessor.colors.surfaceRaised,
                    shape = panelShape,
                )
                .border(
                    width = StratumThemeAccessor.shapes.borderWidth,
                    color = StratumThemeAccessor.colors.borderSubtle,
                    shape = panelShape,
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StratumText(
                text = session.workoutName,
                style = StratumThemeAccessor.typography.titleLarge,
            )
            StratumText(
                text = session.completedAtLabel,
                style = StratumThemeAccessor.typography.bodyLarge,
                muted = true,
            )
        }
        SessionStatsRow(
            durationFormatted = session.durationFormatted,
            totalSetsLabel = session.totalSetsLabel,
            personalRecordsLabel = session.personalRecordsLabel,
        )
    }
}

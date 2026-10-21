package com.stratum.feature.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.component.StratumBottomSheet
import com.stratum.core.ui.component.StratumDivider
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumPreferenceRow
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumSpacing
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R
import com.stratum.feature.history.ui.calendar.HistoryMonthBlock
import com.stratum.feature.history.ui.calendar.HistoryMultiYearBlock
import com.stratum.feature.history.ui.calendar.HistoryYearBlock
import java.time.YearMonth

@Composable
fun WorkoutHistoryScreen(
    state: WorkoutHistoryUiState,
    onBackClick: () -> Unit,
    onModeSelected: (HistoryCalendarMode) -> Unit,
    onMonthClick: (YearMonth) -> Unit,
    onYearClick: (year: Int) -> Unit,
    onDayClick: (day: HistoryDayUi) -> Unit,
    onScrollToMonthConsumed: () -> Unit,
    onScrollToYearConsumed: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showModeSheet by remember { mutableStateOf(false) }
    val modeLabel =
        when (state) {
            is WorkoutHistoryUiState.Loaded -> modeLabel(state.mode)
            else -> stringResource(R.string.history_mode_month)
        }

    StratumScaffold(
        modifier = modifier,
        topBar = {
            StratumTopBar(
                titleContent = {
                    Row(
                        modifier = Modifier.clickable { showModeSheet = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
                    ) {
                        StratumText(
                            text = modeLabel,
                            style = StratumThemeAccessor.typography.titleLarge,
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = StratumThemeAccessor.colors.textPrimary,
                        )
                    }
                },
                navigationIcon = {
                    StratumTopBarBackButton(
                        onClick = onBackClick,
                        contentDescription = stringResource(R.string.history_navigate_back),
                    )
                },
                bottom = {
                    if (state is WorkoutHistoryUiState.Loaded) {
                        HistoryStatsStrip(
                            weekStreak = state.weekStreak,
                            restDaysCount = state.restDaysCount,
                        )
                        StratumDivider()
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            WorkoutHistoryUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(text = "…", muted = true)
                }
            WorkoutHistoryUiState.Error ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    StratumFeatureErrorFallback(onRetry = onRetry)
                }
            is WorkoutHistoryUiState.Loaded ->
                HistoryCalendarBody(
                    state = state,
                    onMonthClick = onMonthClick,
                    onYearClick = onYearClick,
                    onDayClick = onDayClick,
                    onScrollToMonthConsumed = onScrollToMonthConsumed,
                    onScrollToYearConsumed = onScrollToYearConsumed,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                )
        }
    }

    if (showModeSheet) {
        HistoryModeSheet(
            selected = (state as? WorkoutHistoryUiState.Loaded)?.mode ?: HistoryCalendarMode.Month,
            onDismiss = { showModeSheet = false },
            onModeSelected = { mode ->
                onModeSelected(mode)
                showModeSheet = false
            },
        )
    }
}

@Composable
private fun HistoryStatsStrip(
    weekStreak: Int,
    restDaysCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = StratumSpacing.s16, vertical = StratumSpacing.s12),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = StratumThemeAccessor.colors.accent,
            )
            StratumText(
                text = stringResource(R.string.history_week_streak, weekStreak),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(StratumSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.NightsStay,
                contentDescription = null,
                tint = StratumThemeAccessor.colors.textMuted,
            )
            StratumText(
                text = stringResource(R.string.history_rest_days, restDaysCount),
                style = StratumThemeAccessor.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HistoryCalendarBody(
    state: WorkoutHistoryUiState.Loaded,
    onMonthClick: (YearMonth) -> Unit,
    onYearClick: (year: Int) -> Unit,
    onDayClick: (day: HistoryDayUi) -> Unit,
    onScrollToMonthConsumed: () -> Unit,
    onScrollToYearConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.mode) {
        HistoryCalendarMode.Month -> {
            val listState = rememberLazyListState()
            LaunchedEffect(state.scrollToMonth, state.months) {
                val target = state.scrollToMonth ?: return@LaunchedEffect
                val index = state.months.indexOfFirst { it.yearMonth == target }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
                onScrollToMonthConsumed()
            }
            LazyColumn(modifier = modifier, state = listState) {
                if (state.months.isEmpty() || state.months.all { month -> month.days.none { it.hasWorkout } }) {
                    item {
                        StratumText(
                            text = stringResource(R.string.history_empty),
                            muted = true,
                            modifier = Modifier.padding(StratumSpacing.s16),
                        )
                    }
                }
                items(
                    items = state.months,
                    key = { it.yearMonth.toString() },
                ) { month ->
                    HistoryMonthBlock(
                        month = month,
                        onDayClick = onDayClick,
                    )
                }
            }
        }
        HistoryCalendarMode.Year -> {
            val listState = rememberLazyListState()
            LaunchedEffect(state.scrollToYear, state.years) {
                val target = state.scrollToYear ?: return@LaunchedEffect
                val index = state.years.indexOfFirst { it.year == target }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
                onScrollToYearConsumed()
            }
            LazyColumn(modifier = modifier, state = listState) {
                items(
                    items = state.years,
                    key = { it.year },
                ) { year ->
                    HistoryYearBlock(
                        year = year,
                        onMonthClick = onMonthClick,
                    )
                }
            }
        }
        HistoryCalendarMode.MultiYear -> {
            LazyColumn(modifier = modifier) {
                items(
                    items = state.years,
                    key = { it.year },
                ) { year ->
                    HistoryMultiYearBlock(
                        year = year,
                        onYearClick = onYearClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryModeSheet(
    selected: HistoryCalendarMode,
    onDismiss: () -> Unit,
    onModeSelected: (HistoryCalendarMode) -> Unit,
) {
    StratumBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.history_mode_sheet_title),
    ) {
        HistoryCalendarMode.entries.forEach { mode ->
            StratumPreferenceRow(
                title = modeLabel(mode),
                subtitle = if (mode == selected) {
                    stringResource(R.string.history_mode_selected)
                } else {
                    ""
                },
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun modeLabel(mode: HistoryCalendarMode): String =
    when (mode) {
        HistoryCalendarMode.Month -> stringResource(R.string.history_mode_month)
        HistoryCalendarMode.Year -> stringResource(R.string.history_mode_year)
        HistoryCalendarMode.MultiYear -> stringResource(R.string.history_mode_multi_year)
    }

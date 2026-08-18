package com.stratum.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stratum.core.ui.R as CoreUiR
import com.stratum.core.ui.component.RirTargetBadge
import com.stratum.core.ui.component.StratumFeatureErrorFallback
import com.stratum.core.ui.component.StratumNumber
import com.stratum.core.ui.component.StratumScaffold
import com.stratum.core.ui.component.StratumText
import com.stratum.core.ui.component.StratumTextButton
import com.stratum.core.ui.component.StratumTopBar
import com.stratum.core.ui.component.StratumTopBarBackButton
import com.stratum.core.ui.theme.StratumThemeAccessor
import com.stratum.feature.history.R

@Composable
fun WorkoutSessionDetailScreen(
    state: WorkoutSessionDetailUiState,
    onBackClick: () -> Unit,
    onEditWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onRetry: () -> Unit = {},
) {
    StratumScaffold(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            StratumTopBar(
                title =
                    when (state) {
                        is WorkoutSessionDetailUiState.Loaded -> state.workoutName
                        else -> ""
                    },
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
            WorkoutSessionDetailUiState.Loading ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    StratumText(text = "…", muted = true)
                }
            WorkoutSessionDetailUiState.Error ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) {
                    StratumFeatureErrorFallback(onRetry = onRetry)
                }
            is WorkoutSessionDetailUiState.Loaded ->
                SessionDetailContent(
                    state = state,
                    onEditWorkoutClick = onEditWorkoutClick,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                )
        }
    }
}

@Composable
private fun SessionDetailContent(
    state: WorkoutSessionDetailUiState.Loaded,
    onEditWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionSummaryContent(
            workoutName = state.workoutName,
            completedAtLabel = state.completedAtLabel,
            durationFormatted = state.durationFormatted,
            totalSetsLabel = state.totalSetsLabel,
            personalRecordsLabel = state.personalRecordsLabel,
            upperVolumeByMuscle = state.upperVolumeByMuscle,
            lowerVolumeByMuscle = state.lowerVolumeByMuscle,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StratumText(
                text = stringResource(R.string.history_session_workout_section),
                style = StratumThemeAccessor.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            StratumTextButton(
                text = stringResource(R.string.history_session_edit),
                onClick = onEditWorkoutClick,
            )
        }

        state.lifts.forEach { lift ->
            HistoryLiftBlock(lift = lift)
        }
    }
}

private val HistoryRirBadgeSize = 24.dp

@Composable
private fun HistoryLiftBlock(lift: HistoryLiftUi) {
    val panelShape = StratumThemeAccessor.shapes.panel
    Column(
        modifier =
            Modifier
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
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StratumText(
            text = lift.liftName,
            style =
                StratumThemeAccessor.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        lift.sets.forEach { set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StratumNumber(
                        text = set.performanceLabel,
                        style = StratumThemeAccessor.typography.dataLarge,
                    )
                    set.rir?.let { rir ->
                        RirTargetBadge(
                            rirGoal = rir,
                            onClick = {},
                            enabled = false,
                            size = HistoryRirBadgeSize,
                        )
                    }
                }
                if (set.isPersonalRecord) {
                    Icon(
                        painter = painterResource(CoreUiR.drawable.ic_personal_record),
                        contentDescription = stringResource(R.string.history_session_pr_cd),
                        tint = StratumThemeAccessor.colors.prHighlight,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Box(modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

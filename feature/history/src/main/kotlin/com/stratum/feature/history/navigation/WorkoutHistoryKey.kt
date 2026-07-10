package com.stratum.feature.history.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.history.WorkoutHistoryViewModel
import com.stratum.feature.history.WorkoutSessionDetailViewModel
import com.stratum.feature.history.ui.WorkoutHistoryScreen
import com.stratum.feature.history.ui.WorkoutSessionDetailScreen
import com.stratum.feature.history.R
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.Serializable

/** Nav3 key for the workout history calendar screen. */
@Serializable
data object WorkoutHistoryKey : NavKey

/** Nav3 key for a completed session detail screen. */
@Serializable
data class WorkoutSessionDetailKey(
    val sessionId: String,
) : NavKey

fun EntryProviderScope<NavKey>.workoutHistoryEntry(
    onBackClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
) {
    entry<WorkoutHistoryKey> {
        WorkoutHistoryRoute(
            onBackClick = onBackClick,
            onSessionClick = onSessionClick,
        )
    }
}

fun EntryProviderScope<NavKey>.workoutSessionDetailEntry(
    onBackClick: () -> Unit,
) {
    entry<WorkoutSessionDetailKey> { key ->
        WorkoutSessionDetailRoute(
            sessionId = key.sessionId,
            onBackClick = onBackClick,
        )
    }
}

@Composable
private fun WorkoutHistoryRoute(
    onBackClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutHistoryScreen(
        state = uiState,
        onBackClick = onBackClick,
        onModeSelected = viewModel::onModeSelected,
        onMonthClick = viewModel::onYearMonthClick,
        onYearClick = viewModel::onYearClick,
        onSessionClick = onSessionClick,
        onScrollToMonthConsumed = viewModel::onScrollToMonthConsumed,
        onScrollToYearConsumed = viewModel::onScrollToYearConsumed,
    )
}

@Composable
private fun WorkoutSessionDetailRoute(
    sessionId: String,
    onBackClick: () -> Unit,
    viewModel: WorkoutSessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val editComingSoon = stringResource(R.string.history_session_edit_coming_soon)

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    WorkoutSessionDetailScreen(
        state = uiState,
        onBackClick = onBackClick,
        onEditWorkoutClick = { viewModel.onEditWorkoutClick(editComingSoon) },
        snackbarHostState = snackbarHostState,
    )
}

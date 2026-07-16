package com.stratum.feature.history.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.UserMessageEffect
import com.stratum.feature.history.EditHistoricalWorkoutViewModel
import com.stratum.feature.history.HistoryDaySessionsViewModel
import com.stratum.feature.history.WorkoutHistoryViewModel
import com.stratum.feature.history.WorkoutSessionDetailViewModel
import com.stratum.feature.history.ui.EditHistoricalWorkoutEvent
import com.stratum.feature.history.ui.EditHistoricalWorkoutScreen
import com.stratum.feature.history.ui.HistoryDaySessionsScreen
import com.stratum.feature.history.ui.HistoryDayUi
import com.stratum.feature.history.ui.WorkoutHistoryScreen
import com.stratum.feature.history.ui.WorkoutSessionDetailScreen
import kotlinx.serialization.Serializable

/** Nav3 key for the workout history calendar screen. */
@Serializable
data object WorkoutHistoryKey : NavKey

/**
 * Nav3 key for a multi-workout day list. [dateIso] is `LocalDate.toString()` (yyyy-MM-dd).
 * Session ids are resolved live from the calendar on load/resume (not frozen in the key).
 */
@Serializable
data class HistoryDaySessionsKey(
    val dateIso: String,
) : NavKey

/** Nav3 key for a completed session detail screen. */
@Serializable
data class WorkoutSessionDetailKey(
    val sessionId: String,
) : NavKey

/** Nav3 key for editing a completed session's logged sets. */
@Serializable
data class EditHistoricalWorkoutKey(
    val sessionId: String,
) : NavKey

fun EntryProviderScope<NavKey>.workoutHistoryEntry(
    onBackClick: () -> Unit,
    onDayClick: (day: HistoryDayUi) -> Unit,
) {
    entry<WorkoutHistoryKey> {
        WorkoutHistoryRoute(
            onBackClick = onBackClick,
            onDayClick = onDayClick,
        )
    }
}

fun EntryProviderScope<NavKey>.historyDaySessionsEntry(
    onBackClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
) {
    entry<HistoryDaySessionsKey> { key ->
        HistoryDaySessionsRoute(
            dateIso = key.dateIso,
            onBackClick = onBackClick,
            onSessionClick = onSessionClick,
        )
    }
}

fun EntryProviderScope<NavKey>.workoutSessionDetailEntry(
    onBackClick: () -> Unit,
    onEditWorkoutClick: (sessionId: String) -> Unit,
) {
    entry<WorkoutSessionDetailKey> { key ->
        WorkoutSessionDetailRoute(
            sessionId = key.sessionId,
            onBackClick = onBackClick,
            onEditWorkoutClick = { onEditWorkoutClick(key.sessionId) },
        )
    }
}

fun EntryProviderScope<NavKey>.editHistoricalWorkoutEntry(
    onBackClick: () -> Unit,
) {
    entry<EditHistoricalWorkoutKey> { key ->
        EditHistoricalWorkoutRoute(
            sessionId = key.sessionId,
            onBackClick = onBackClick,
        )
    }
}

@Composable
private fun WorkoutHistoryRoute(
    onBackClick: () -> Unit,
    onDayClick: (day: HistoryDayUi) -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutHistoryScreen(
        state = uiState,
        onBackClick = onBackClick,
        onModeSelected = viewModel::onModeSelected,
        onMonthClick = viewModel::onYearMonthClick,
        onYearClick = viewModel::onYearClick,
        onDayClick = onDayClick,
        onScrollToMonthConsumed = viewModel::onScrollToMonthConsumed,
        onScrollToYearConsumed = viewModel::onScrollToYearConsumed,
    )
}

@Composable
private fun HistoryDaySessionsRoute(
    dateIso: String,
    onBackClick: () -> Unit,
    onSessionClick: (sessionId: String) -> Unit,
    viewModel: HistoryDaySessionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Reload on every resume so edits/deletes on a detail screen refresh the day list.
    DisposableEffect(lifecycleOwner, dateIso) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.load(dateIso = dateIso)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HistoryDaySessionsScreen(
        state = uiState,
        onBackClick = onBackClick,
        onSessionClick = onSessionClick,
    )
}

@Composable
private fun WorkoutSessionDetailRoute(
    sessionId: String,
    onBackClick: () -> Unit,
    onEditWorkoutClick: () -> Unit,
    viewModel: WorkoutSessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, sessionId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.load(sessionId)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    WorkoutSessionDetailScreen(
        state = uiState,
        onBackClick = onBackClick,
        onEditWorkoutClick = onEditWorkoutClick,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun EditHistoricalWorkoutRoute(
    sessionId: String,
    onBackClick: () -> Unit,
    viewModel: EditHistoricalWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                EditHistoricalWorkoutEvent.NavigateBack -> onBackClick()
            }
        }
    }
    UserMessageEffect(
        userMessage = userMessage,
        snackbarHostState = snackbarHostState,
        onUserMessageShown = viewModel::userMessageShown,
    )
    EditHistoricalWorkoutScreen(
        state = uiState,
        onBackClick = onBackClick,
        onConfirmClick = viewModel::confirm,
        onWeightChange = viewModel::onWeightChange,
        onRepsChange = viewModel::onRepsChange,
        onRirChange = viewModel::onRirChange,
        onAddSet = viewModel::onAddSet,
        onDeleteSet = viewModel::onDeleteSet,
        snackbarHostState = snackbarHostState,
    )
}

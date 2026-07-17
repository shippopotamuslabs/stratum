package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.ObserveWorkoutHistoryCalendarUseCase
import com.stratum.feature.history.ui.HistoryDaySessionsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryDaySessionsViewModel @Inject constructor(
    private val observeWorkoutHistoryCalendar: ObserveWorkoutHistoryCalendarUseCase,
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<HistoryDaySessionsUiState>(HistoryDaySessionsUiState.Loading)
    val uiState: StateFlow<HistoryDaySessionsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Loads (or reloads) completed sessions for [dateIso] (`LocalDate.toString()`).
     * Session ids come from the live calendar — not a nav snapshot — so returning from edit
     * refreshes the list. Any single detail failure fails closed to [HistoryDaySessionsUiState.Error].
     */
    fun load(dateIso: String, zoneId: ZoneId = ZoneId.systemDefault()) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _uiState.value = HistoryDaySessionsUiState.Loading
                val date =
                    runCatching { LocalDate.parse(dateIso) }.getOrElse {
                        _uiState.value = HistoryDaySessionsUiState.Error
                        return@launch
                    }
                val daySessions =
                    observeWorkoutHistoryCalendar(zoneId = zoneId)
                        .first()
                        .days[date]
                        .orEmpty()
                if (daySessions.isEmpty()) {
                    _uiState.value = HistoryDaySessionsUiState.Error
                    return@launch
                }
                val results =
                    coroutineScope {
                        daySessions
                            .map { daySession ->
                                async {
                                    getWorkoutSessionDetail(daySession.sessionId)
                                        .map(WorkoutSessionDetailUiMapper::toDaySummary)
                                }
                            }
                            .awaitAll()
                    }
                // Fail closed: a missing/corrupt session must not silently shrink the day list.
                if (results.any { it.isFailure }) {
                    _uiState.value = HistoryDaySessionsUiState.Error
                    return@launch
                }
                _uiState.value =
                    HistoryDaySessionsUiState.Loaded(
                        dateTitle = WorkoutSessionDetailUiMapper.formatDayTitle(dateIso),
                        sessions = results.map { it.getOrThrow() },
                    )
            }
    }
}

package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.HistoryDaySessionsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryDaySessionsViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<HistoryDaySessionsUiState>(HistoryDaySessionsUiState.Loading)
    val uiState: StateFlow<HistoryDaySessionsUiState> = _uiState.asStateFlow()

    fun load(dateIso: String, sessionIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = HistoryDaySessionsUiState.Loading
            if (sessionIds.isEmpty()) {
                _uiState.value = HistoryDaySessionsUiState.Error
                return@launch
            }
            val summaries =
                coroutineScope {
                    sessionIds
                        .map { sessionId ->
                            async {
                                getWorkoutSessionDetail(TrainingSessionId(sessionId))
                                    .map(WorkoutSessionDetailUiMapper::toDaySummary)
                                    .getOrNull()
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                }
            _uiState.value =
                if (summaries.isEmpty()) {
                    HistoryDaySessionsUiState.Error
                } else {
                    HistoryDaySessionsUiState.Loaded(
                        dateTitle = WorkoutSessionDetailUiMapper.formatDayTitle(dateIso),
                        sessions = summaries,
                    )
                }
        }
    }
}

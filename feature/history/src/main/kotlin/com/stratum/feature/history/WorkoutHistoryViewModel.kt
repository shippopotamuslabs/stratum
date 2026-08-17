package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.training.ObserveWorkoutHistoryCalendarUseCase
import com.stratum.feature.history.ui.HistoryCalendarMode
import com.stratum.feature.history.ui.WorkoutHistoryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.loggedViewModelScope

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val observeWorkoutHistoryCalendar: ObserveWorkoutHistoryCalendarUseCase,
    logError: LogErrorUseCase = LogErrorUseCase(com.stratum.domain.logging.SilentLoggingPort),
) : ViewModel(loggedViewModelScope("WorkoutHistoryViewModel", logError)) {

    private val mode = MutableStateFlow(HistoryCalendarMode.Month)
    private val scrollToMonth = MutableStateFlow<YearMonth?>(null)
    private val scrollToYear = MutableStateFlow<Int?>(null)

    private val _uiState =
        MutableStateFlow<WorkoutHistoryUiState>(WorkoutHistoryUiState.Loading)
    val uiState: StateFlow<WorkoutHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val zoneId = ZoneId.systemDefault()
            combine(
                observeWorkoutHistoryCalendar(zoneId = zoneId),
                mode,
                scrollToMonth,
                scrollToYear,
            ) { calendar, calendarMode, pendingMonth, pendingYear ->
                WorkoutHistoryMapper.toUiState(
                    calendar = calendar,
                    mode = calendarMode,
                    today = LocalDate.now(zoneId),
                    scrollToMonth = pendingMonth,
                    scrollToYear = pendingYear,
                )
            }.collect { loaded ->
                _uiState.value = loaded
            }
        }
    }

    fun onModeSelected(selected: HistoryCalendarMode) {
        mode.value = selected
    }

    fun onYearMonthClick(yearMonth: YearMonth) {
        mode.value = HistoryCalendarMode.Month
        scrollToMonth.value = yearMonth
    }

    /** Multi-year year row → Year mode, scrolled to that year. */
    fun onYearClick(year: Int) {
        mode.value = HistoryCalendarMode.Year
        scrollToYear.value = year
    }

    fun onScrollToMonthConsumed() {
        scrollToMonth.update { null }
    }

    fun onScrollToYearConsumed() {
        scrollToYear.update { null }
    }
}

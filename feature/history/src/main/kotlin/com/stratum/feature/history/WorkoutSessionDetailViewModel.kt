package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.application.training.GetWorkoutSessionDetailResult
import com.stratum.application.training.GetWorkoutSessionDetailUseCase
import com.stratum.application.training.UpdateCompletedSessionTimesResult
import com.stratum.application.training.UpdateCompletedSessionTimesUseCase
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
    private val updateCompletedSessionTimes: UpdateCompletedSessionTimesUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WorkoutSessionDetailUiState>(WorkoutSessionDetailUiState.Loading)
    val uiState: StateFlow<WorkoutSessionDetailUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var currentSessionId: String? = null
    private var cachedDetail: WorkoutSessionDetail? = null
    private var displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb
    private var isUpdating: Boolean = false

    init {
        viewModelScope.launch {
            observeWeightDisplayPreferences().collect { prefs ->
                if (displayUnit == prefs.displayUnit) return@collect
                displayUnit = prefs.displayUnit
                cachedDetail?.let { detail ->
                    _uiState.value =
                        WorkoutSessionDetailUiMapper.toDetailLoaded(detail, displayUnit)
                }
            }
        }
    }

    fun load(sessionId: String) {
        val isFirstLoadForSession =
            currentSessionId != sessionId || _uiState.value !is WorkoutSessionDetailUiState.Loaded
        currentSessionId = sessionId
        viewModelScope.launch {
            loadInternal(sessionId, isInitial = isFirstLoadForSession)
        }
    }

    fun updateWorkoutTimes(
        startDate: LocalDate,
        startTime: LocalTime,
        finishDate: LocalDate,
        finishTime: LocalTime,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        val sessionId = currentSessionId ?: return
        if (isUpdating) return
        val startedAt = startDate.atTime(startTime).atZone(zoneId).toInstant()
        val endedAt = finishDate.atTime(finishTime).atZone(zoneId).toInstant()

        viewModelScope.launch {
            isUpdating = true
            try {
                val result =
                    updateCompletedSessionTimes(
                        sessionId = TrainingSessionId(sessionId),
                        startedAt = startedAt,
                        endedAt = endedAt,
                    )
                when (result) {
                    UpdateCompletedSessionTimesResult.Success ->
                        loadInternal(sessionId, isInitial = false, zoneId = zoneId)
                    else ->
                        // Same user copy the former exception messages carried, sourced
                        // from the branch kind (STR-243).
                        _userMessage.value =
                            when (result) {
                                is UpdateCompletedSessionTimesResult.SessionNotFound ->
                                    "Training session ${result.sessionId.value} not found"
                                is UpdateCompletedSessionTimesResult.SessionNotCompleted ->
                                    "Training session ${result.sessionId.value} is not completed " +
                                        "(status=${result.status})"
                                is UpdateCompletedSessionTimesResult.StartAfterFinish ->
                                    "Start time (${result.startedAt}) cannot be after " +
                                        "finish time (${result.endedAt})"
                                UpdateCompletedSessionTimesResult.PersistenceFailed ->
                                    "Could not update workout time"
                                UpdateCompletedSessionTimesResult.Success ->
                                    "Could not update workout time"
                            }
                }
            } finally {
                isUpdating = false
            }
        }
    }

    private suspend fun loadInternal(
        sessionId: String,
        isInitial: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (isInitial) {
            _uiState.value = WorkoutSessionDetailUiState.Loading
        }
        displayUnit = observeWeightDisplayPreferences().first().displayUnit
        val result = getWorkoutSessionDetail(TrainingSessionId(sessionId))
        _uiState.value =
            when (result) {
                is GetWorkoutSessionDetailResult.Success -> {
                    cachedDetail = result.detail
                    WorkoutSessionDetailUiMapper.toDetailLoaded(result.detail, displayUnit, zoneId)
                }
                else -> {
                    cachedDetail = null
                    WorkoutSessionDetailUiState.Error
                }
            }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}

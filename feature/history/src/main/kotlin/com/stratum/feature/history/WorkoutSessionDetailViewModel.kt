package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.UpdateCompletedSessionTimesUseCase
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
                result.fold(
                    onSuccess = {
                        loadInternal(sessionId, isInitial = false, zoneId = zoneId)
                    },
                    onFailure = { error ->
                        _userMessage.value = error.message ?: "Could not update workout time"
                    },
                )
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
            result.fold(
                onSuccess = { detail ->
                    cachedDetail = detail
                    WorkoutSessionDetailUiMapper.toDetailLoaded(detail, displayUnit, zoneId)
                },
                onFailure = {
                    cachedDetail = null
                    WorkoutSessionDetailUiState.Error
                },
            )
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}

package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.loggedViewModelScope

@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
    logError: LogErrorUseCase = LogErrorUseCase(com.stratum.domain.logging.SilentLoggingPort),
) : ViewModel(loggedViewModelScope("WorkoutSessionDetailViewModel", logError)) {

    private val _uiState =
        MutableStateFlow<WorkoutSessionDetailUiState>(WorkoutSessionDetailUiState.Loading)
    val uiState: StateFlow<WorkoutSessionDetailUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var cachedDetail: WorkoutSessionDetail? = null
    private var displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb

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
        viewModelScope.launch {
            _uiState.value = WorkoutSessionDetailUiState.Loading
            displayUnit = observeWeightDisplayPreferences().first().displayUnit
            val result = getWorkoutSessionDetail(TrainingSessionId(sessionId))
            _uiState.value =
                result.fold(
                    onSuccess = { detail ->
                        cachedDetail = detail
                        WorkoutSessionDetailUiMapper.toDetailLoaded(detail, displayUnit)
                    },
                    onFailure = {
                        cachedDetail = null
                        WorkoutSessionDetailUiState.Error
                    },
                )
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}

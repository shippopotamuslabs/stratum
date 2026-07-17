package com.stratum.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.training.GetWorkoutSessionDetailUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.history.ui.WorkoutSessionDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val getWorkoutSessionDetail: GetWorkoutSessionDetailUseCase,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<WorkoutSessionDetailUiState>(WorkoutSessionDetailUiState.Loading)
    val uiState: StateFlow<WorkoutSessionDetailUiState> = _uiState.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = WorkoutSessionDetailUiState.Loading
            val result = getWorkoutSessionDetail(TrainingSessionId(sessionId))
            _uiState.value =
                result.fold(
                    onSuccess = WorkoutSessionDetailUiMapper::toDetailLoaded,
                    onFailure = { WorkoutSessionDetailUiState.Error },
                )
        }
    }

    fun userMessageShown() {
        _userMessage.value = null
    }
}

package com.stratum.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.OnboardingStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    val uiState: StateFlow<OnboardingUiState> =
        repository.observeProgress()
            .map { progress -> progress.toUiState() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = OnboardingUiState.Initial,
            )

    fun next() {
        val current = uiState.value.step
        val nextStep = current.next() ?: return
        saveStep(nextStep)
    }

    fun back() {
        val current = uiState.value.step
        val previousStep = current.previous() ?: return
        saveStep(previousStep)
    }

    fun complete() {
        viewModelScope.launch { repository.markComplete() }
    }

    private fun saveStep(step: OnboardingStep) {
        viewModelScope.launch { repository.saveStep(step) }
    }

    private fun OnboardingProgress?.toUiState(): OnboardingUiState =
        if (this == null) {
            OnboardingUiState.Initial
        } else {
            OnboardingUiState(step = currentStep, isComplete = isComplete)
        }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

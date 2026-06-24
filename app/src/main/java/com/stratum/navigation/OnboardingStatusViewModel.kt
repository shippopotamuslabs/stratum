package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.ObserveOnboardingProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class OnboardingStatusViewModel @Inject constructor(
    observe: ObserveOnboardingProgressUseCase,
) : ViewModel() {
    val isComplete: StateFlow<Boolean> =
        observe()
            .map { progress -> progress?.isComplete == true }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = false,
            )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

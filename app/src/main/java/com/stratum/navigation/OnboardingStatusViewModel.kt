package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.onboarding.ReopenOnboardingWizardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingStatusViewModel @Inject constructor(
    observeComplete: ObserveOnboardingCompleteUseCase,
    private val reopenOnboardingWizard: ReopenOnboardingWizardUseCase,
) : ViewModel() {
    /**
     * `null` until the first repository emission — callers must not route on `false` while
     * still unknown to avoid a cold-start onboarding flash for returning users.
     */
    val isComplete: StateFlow<Boolean?> =
        observeComplete()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    fun reopenOnboarding() {
        viewModelScope.launch {
            // Failure is logged in the use case; navigation is driven by isComplete flow.
            reopenOnboardingWizard()
        }
    }
}


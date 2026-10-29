package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.application.onboarding.ReopenOnboardingWizardUseCase
import com.stratum.application.program.DiscardActiveProgramUseCase
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
    private val discardActiveProgram: DiscardActiveProgramUseCase,
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

    /**
     * Preview "Discard Program": delete the active template (if any), then reopen the wizard
     * at Choose Path. Failures are logged in use cases; onboarding still reopens so the user
     * is not stuck on preview.
     */
    fun discardProgramAndReopenOnboarding() {
        viewModelScope.launch {
            discardActiveProgram()
            reopenOnboardingWizard()
        }
    }
}

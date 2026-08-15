package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class EntitlementGateViewModel @Inject constructor(
    observeEntitlement: ObserveEntitlementUseCase,
    observeOnboardingComplete: ObserveOnboardingCompleteUseCase,
) : ViewModel() {
    val entitlement: StateFlow<EntitlementState> =
        observeEntitlement()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = EntitlementState.Loading,
            )

    private val isComplete: StateFlow<Boolean?> =
        observeOnboardingComplete()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null,
            )

    val destination: StateFlow<AppGateDestination> =
        combine(isComplete, entitlement, ::resolveAppGateDestination)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppGateDestination.Loading,
            )
}

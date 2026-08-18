package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.domain.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntitlementGateViewModel(
    private val observeEntitlement: ObserveEntitlementUseCase,
    private val observeOnboardingComplete: ObserveOnboardingCompleteUseCase,
    private val loadingTimeoutMs: Long,
) : ViewModel() {
    @Inject
    constructor(
        observeEntitlement: ObserveEntitlementUseCase,
        observeOnboardingComplete: ObserveOnboardingCompleteUseCase,
    ) : this(
        observeEntitlement,
        observeOnboardingComplete,
        DEFAULT_GATE_LOADING_TIMEOUT_MS,
    )

    private val retryTick = MutableStateFlow(0)

    val destination: StateFlow<AppGateDestination> =
        retryTick
            .flatMapLatest {
                combine(
                    observeOnboardingComplete(),
                    observeEntitlement(),
                    loadingTimeout(),
                ) { complete, entitlement, timedOut ->
                    val resolved = resolveAppGateDestination(complete, entitlement)
                    if (timedOut && resolved == AppGateDestination.Loading) {
                        AppGateDestination.Failed
                    } else {
                        resolved
                    }
                }.catch { emit(AppGateDestination.Failed) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppGateDestination.Loading,
            )

    fun retry() {
        retryTick.update { it + 1 }
    }

    private fun loadingTimeout() = flow {
        emit(false)
        delay(loadingTimeoutMs)
        emit(true)
    }

    internal companion object {
        const val DEFAULT_GATE_LOADING_TIMEOUT_MS = 15_000L
    }
}

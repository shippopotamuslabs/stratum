package com.stratum.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stratum.application.subscription.AppGateReadModel
import com.stratum.application.subscription.ObserveAppGateDestinationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class EntitlementGateViewModel(
    observeAppGateDestination: ObserveAppGateDestinationUseCase,
    loadingTimeoutMs: Long,
) : ViewModel() {
    @Inject
    constructor(
        observeAppGateDestination: ObserveAppGateDestinationUseCase,
    ) : this(
        observeAppGateDestination,
        ObserveAppGateDestinationUseCase.DEFAULT_GATE_LOADING_TIMEOUT_MS,
    )

    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val destination: StateFlow<AppGateDestination> =
        observeAppGateDestination(retries, loadingTimeoutMs)
            .map { it.toAppGateDestination() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = AppGateDestination.Loading,
            )

    fun retry() {
        retries.tryEmit(Unit)
    }

    private fun AppGateReadModel.toAppGateDestination(): AppGateDestination =
        when (this) {
            AppGateReadModel.Loading -> AppGateDestination.Loading
            AppGateReadModel.Failed -> AppGateDestination.Failed
            AppGateReadModel.Paywall -> AppGateDestination.Paywall
            AppGateReadModel.Onboarding -> AppGateDestination.Onboarding
            AppGateReadModel.Main -> AppGateDestination.Main
        }
}

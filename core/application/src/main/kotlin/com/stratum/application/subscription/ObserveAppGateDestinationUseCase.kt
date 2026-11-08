package com.stratum.application.subscription

import com.stratum.application.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.subscription.EntitlementState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge

/**
 * Application-layer gate destination (STR-286).
 *
 * Mirrors the presentation `AppGateDestination` without the navigation keys, so the
 * composition can live here per §12.4 while the ViewModel maps it 1:1 at its boundary.
 * `StratumNavHost` still never reads domain entitlement types.
 */
enum class AppGateReadModel {
    Loading,
    Failed,
    Paywall,
    Onboarding,
    Main,
}

/**
 * Owns the app-gate multi-source composition (STR-286).
 *
 * Combines onboarding completeness, entitlement, and the loading timeout into a single
 * destination stream. A failure in any source maps to [AppGateReadModel.Failed]; each
 * [retries] emission resubscribes to all sources so a `retry()` recovers. The initial
 * collection starts the pipeline without waiting for a retry.
 */
class ObserveAppGateDestinationUseCase @Inject constructor(
    private val observeOnboardingComplete: ObserveOnboardingCompleteUseCase,
    private val observeEntitlement: ObserveEntitlementUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        retries: Flow<Unit>,
        loadingTimeoutMs: Long = DEFAULT_GATE_LOADING_TIMEOUT_MS,
    ): Flow<AppGateReadModel> =
        merge(flowOf(Unit), retries)
            .flatMapLatest {
                combine(
                    observeOnboardingComplete(),
                    observeEntitlement(),
                    loadingTimeout(loadingTimeoutMs),
                ) { complete, entitlement, timedOut ->
                    val resolved = resolveAppGateReadModel(complete, entitlement)
                    if (timedOut && resolved == AppGateReadModel.Loading) {
                        AppGateReadModel.Failed
                    } else {
                        resolved
                    }
                }.catch { emit(AppGateReadModel.Failed) }
            }

    private fun loadingTimeout(loadingTimeoutMs: Long) = flow {
        emit(false)
        delay(loadingTimeoutMs)
        emit(true)
    }

    companion object {
        const val DEFAULT_GATE_LOADING_TIMEOUT_MS = 15_000L
    }
}

internal fun resolveAppGateReadModel(
    isComplete: Boolean?,
    entitlement: EntitlementState,
): AppGateReadModel =
    when {
        isComplete == null || entitlement is EntitlementState.Loading ->
            AppGateReadModel.Loading
        entitlement is EntitlementState.NotEntitled ->
            AppGateReadModel.Paywall
        isComplete == false ->
            AppGateReadModel.Onboarding
        else ->
            AppGateReadModel.Main
    }

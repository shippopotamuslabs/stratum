package com.stratum.subscription

import com.stratum.application.subscription.ObserveEntitlementDiagnosticsUseCase
import com.stratum.application.subscription.SUBSCRIPTION_REPO_TAG
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.collectLatestRestarting
import com.stratum.domain.subscription.EntitlementDiagnostic
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App-shell boundary drain for entitlement diagnostics. The repository carries
 * fire-and-forget Play sync failures as [EntitlementDiagnostic] values instead
 * of logging them; each one is logged here exactly once and never reaches the UI.
 */
@Singleton
class EntitlementDiagnosticsListener @Inject constructor(
    private val observeDiagnostics: ObserveEntitlementDiagnosticsUseCase,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            observeDiagnostics().collectLatestRestarting(loggingPort, SUBSCRIPTION_REPO_TAG) { diagnostic ->
                when (diagnostic) {
                    is EntitlementDiagnostic.PlaySyncFailed ->
                        loggingPort.error(SUBSCRIPTION_REPO_TAG, "Play entitlement sync failed", diagnostic.cause)
                }
            }
        }
    }
}

package com.stratum.telemetry

import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.collectLatestRestarting
import com.stratum.application.subscription.ObserveEntitlementUseCase
import com.stratum.application.subscription.SUBSCRIPTION_REPO_TAG
import com.stratum.domain.telemetry.CrashTelemetryPort
import com.stratum.domain.telemetry.toTelemetryTier
import com.stratum.application.training.ObserveActiveSessionUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Binds active-workout and subscription custom keys on [CrashTelemetryPort] so Crashlytics
 * reports include session context without PII.
 */
@Singleton
class TelemetryContextListener @Inject constructor(
    private val observeEntitlement: ObserveEntitlementUseCase,
    private val observeActiveSession: ObserveActiveSessionUseCase,
    private val crashTelemetryPort: CrashTelemetryPort,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                supervisorScope {
                    launch {
                        // Single drain point for the entitlement-changed debug: the repository
                        // flow is distinctUntilChanged, and diagnostics/sync errors are drained
                        // by EntitlementDiagnosticsListener — do not add another collector.
                        observeEntitlement().collectLatestRestarting(loggingPort, TAG) { state ->
                            loggingPort.debug(SUBSCRIPTION_REPO_TAG, "Entitlement changed: $state")
                            crashTelemetryPort.setSubscriptionTier(state.toTelemetryTier())
                        }
                    }
                    launch {
                        observeActiveSession().collectLatestRestarting(loggingPort, TAG) { session ->
                            crashTelemetryPort.setActiveWorkoutId(session?.workoutId?.value)
                        }
                    }
                }
            }
    }

    private companion object {
        const val TAG = "TelemetryContext"
    }
}

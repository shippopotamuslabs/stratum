package com.stratum.telemetry

import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.subscription.ObserveEntitlementUseCase
import com.stratum.domain.telemetry.CrashTelemetryPort
import com.stratum.domain.telemetry.toTelemetryTier
import com.stratum.domain.training.ObserveActiveSessionUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Binds active-workout and subscription custom keys on [CrashTelemetryPort] so Crashlytics
 * reports include session context without PII.
 */
@Singleton
class TelemetryContextListener @Inject constructor(
    private val observeEntitlement: ObserveEntitlementUseCase,
    private val observeActiveSession: ObserveActiveSessionUseCase,
    private val crashTelemetryPort: CrashTelemetryPort,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                launch {
                    observeEntitlement().collectLatest { state ->
                        crashTelemetryPort.setSubscriptionTier(state.toTelemetryTier())
                    }
                }
                launch {
                    observeActiveSession().collectLatest { session ->
                        crashTelemetryPort.setActiveWorkoutId(session?.workoutId?.value)
                    }
                }
            }
    }
}

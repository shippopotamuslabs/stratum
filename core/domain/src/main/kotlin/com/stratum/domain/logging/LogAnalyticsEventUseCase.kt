package com.stratum.domain.logging

import com.stratum.domain.telemetry.AnalyticsPort
import javax.inject.Inject

/** Stable vocabulary for product analytics events recorded through [LogAnalyticsEventUseCase]. */
const val USER_VISIBLE_ERROR_EVENT = "user_visible_error"
const val PARAM_SCREEN = "screen"
const val PARAM_ACTION = "action"

/**
 * Thin wrapper around [AnalyticsPort.logEvent] for presentation-layer instrumentation.
 *
 * Mirrors [LogErrorUseCase]: ViewModels must not inject [AnalyticsPort] directly
 * (ARCHITECTURE.md §4.4 / ForbiddenPortInViewModel). Record only user-visible outcomes —
 * successes that complete a funnel or operational failures surfaced in UI — never
 * field-level validation slips or user cancellations.
 */
class LogAnalyticsEventUseCase @Inject constructor(
    private val analyticsPort: AnalyticsPort,
) {
    operator fun invoke(
        name: String,
        params: Map<String, String> = emptyMap(),
    ) {
        analyticsPort.logEvent(name, params)
    }
}

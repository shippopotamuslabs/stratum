package com.stratum.feature.program

import com.stratum.core.infrastructure.telemetry.AnalyticsPort
import javax.inject.Inject

/**
 * Presentation boundary component (STR-237): records product analytics
 * events through [AnalyticsPort] at the boundary.
 *
 * ViewModels in this module inject this recorder, never a port
 * (ForbiddenPortInViewModel stands). Record only user-visible outcomes -
 * successes that complete a funnel or operational failures surfaced in
 * UI - never field-level validation slips or user cancellations.
 */
class AnalyticsRecorder @Inject constructor(
    private val analyticsPort: AnalyticsPort,
) {
    operator fun invoke(
        name: String,
        params: Map<String, String> = emptyMap(),
    ) {
        analyticsPort.logEvent(name, params)
    }
}

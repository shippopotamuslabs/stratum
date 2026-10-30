package com.stratum.domain.telemetry

import javax.inject.Inject

/**
 * Single routing point for telemetry facts (STR-242).
 *
 * Callers declare what a fact IS, never where it goes: [emitOutcome] takes a
 * classified [OutcomeTelemetry] and [recordExceptional] takes a boundary name
 * plus the throwable, and the router maps each kind to exactly one store.
 * There is no destination parameter anywhere, so misrouting is not expressible
 * and policy changes are a one-file edit.
 *
 * - Outcomes (successes and expected failures) go only to [OutcomeTelemetrySink]
 *   (the authenticated collector transport) — never to Crashlytics.
 * - Exceptionals go only to Crashlytics as non-fatals, plus the bare
 *   [EVENT_EXCEPTIONAL_OCCURRED] counter (a count with boundary/class
 *   attributes, no payload, no stack) for real-time rate alerting. The record
 *   itself lives only in Crashlytics; the counter is a marker, not a copy.
 *
 * Correlation across the two stores: the counter carries [PARAM_BOUNDARY] and
 * [PARAM_CLASS], which match the Crashlytics report's screen breadcrumb and
 * `sanitized_original_class` custom key; both sides carry the app version, and
 * the Analytics-side BigQuery export adds the user/session identity for
 * time-window joins (dashboard queries in slice 3).
 */
class TelemetryRouter @Inject constructor(
    private val outcomeSink: OutcomeTelemetrySink,
    private val crashTelemetry: CrashTelemetryPort,
    private val analytics: AnalyticsPort,
) {
    fun emitOutcome(outcome: OutcomeTelemetry) {
        outcomeSink.record(outcome)
    }

    /**
     * Records an exceptional failure exactly once: a Crashlytics non-fatal plus
     * one counter increment. Terminal boundaries call this INSTEAD of any
     * [com.stratum.domain.logging.LoggingPort] error for the same fact, so the
     * record is never duplicated.
     */
    fun recordExceptional(boundary: String, error: Throwable) {
        crashTelemetry.recordNonFatal(error)
        analytics.logEvent(
            EVENT_EXCEPTIONAL_OCCURRED,
            mapOf(
                PARAM_BOUNDARY to boundary,
                PARAM_CLASS to (error::class.simpleName ?: "Unknown"),
            ),
        )
    }

    companion object {
        const val EVENT_EXCEPTIONAL_OCCURRED = "exceptional_occurred"
        const val PARAM_BOUNDARY = "boundary"
        const val PARAM_CLASS = "class"
    }
}

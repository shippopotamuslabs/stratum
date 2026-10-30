package com.stratum.domain.telemetry

import javax.inject.Inject

/**
 * Single routing point for telemetry facts (STR-242).
 *
 * Callers declare what a fact IS, never where it goes: [emitOutcome] takes a
 * classified [OutcomeTelemetry] and the router maps the kind to exactly one
 * store ([OutcomeTelemetrySink] — the authenticated collector transport). There
 * is no destination parameter anywhere, so misrouting is not expressible and
 * policy changes are a one-file edit.
 *
 * Slice 1 covers outcome emission. Slice 2 adds the exceptional side
 * (`recordExceptional`: Crashlytics non-fatal plus the bare
 * `exceptional_occurred` counter) and slice 4 retires the legacy warning /
 * tier-2 paths that still fan out today.
 */
class TelemetryRouter @Inject constructor(
    private val outcomeSink: OutcomeTelemetrySink,
) {
    fun emitOutcome(outcome: OutcomeTelemetry) {
        outcomeSink.record(outcome)
    }
}

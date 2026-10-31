package com.stratum.core.infrastructure.inference

import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.OutcomeTelemetry
import com.stratum.domain.telemetry.OutcomeTelemetrySink
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OutcomeTelemetrySink] on the authenticated collector transport (STR-242).
 *
 * Maps the outcome fact to a tier-2 [ClientLogBuffer] entry with
 * `OUTCOME` severity and the flat outcome/reasonCode columns, so the series
 * is countable in BigQuery/Grafana. The tier-2 pipeline is reused here
 * explicitly as the collector (ticket item 3): Auth + App Check enforced
 * server-side, no Grafana/Loki write token in the APK. Values travel in the
 * emitter's closed vocabulary — free-text detail stays out so series
 * cardinality stays bounded.
 */
@Singleton
class ClientLogOutcomeSink @Inject constructor(
    private val buffer: ClientLogBuffer,
) : OutcomeTelemetrySink {
    override fun record(outcome: OutcomeTelemetry) {
        buffer.recordOutcome(outcome)
    }
}

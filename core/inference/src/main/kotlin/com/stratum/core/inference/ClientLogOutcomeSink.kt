package com.stratum.core.inference

import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.ClientLogSeverity
import com.stratum.domain.telemetry.OutcomeResult
import com.stratum.domain.telemetry.OutcomeTelemetry
import com.stratum.domain.telemetry.OutcomeTelemetrySink
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [OutcomeTelemetrySink] on the authenticated collector transport (STR-242).
 *
 * Maps the outcome fact to a tier-2 [ClientLogBuffer] entry with
 * [ClientLogSeverity.OUTCOME] and the flat [outcome]/[reasonCode] columns, so
 * the series is countable in BigQuery/Grafana. The tier-2 pipeline is reused
 * here explicitly as the collector (ticket item 3): Auth + App Check enforced
 * server-side, no Grafana/Loki write token in the APK. Messages are scrubbed
 * for PII; outcome vocabulary values come from the emitter's closed set.
 */
@Singleton
class ClientLogOutcomeSink @Inject constructor(
    private val buffer: ClientLogBuffer,
) : OutcomeTelemetrySink {
    override fun record(outcome: OutcomeTelemetry) {
        val reasonCode = (outcome.result as? OutcomeResult.ExpectedFailure)?.reasonCode
        buffer.record(
            severity = ClientLogSeverity.OUTCOME,
            tag = scrubbed(outcome.useCase),
            message =
                scrubbed(
                    buildString {
                        append("outcome=")
                        append(outcome.result.wireName)
                        if (reasonCode != null) {
                            append(" reason=")
                            append(reasonCode)
                        }
                    },
                ),
            durationMs = outcome.durationMs,
            outcome = outcome.result.wireName,
            reasonCode = reasonCode,
        )
    }

    private fun scrubbed(value: String): String =
        TelemetryPiiScrubber.scrub(value).orEmpty()
}

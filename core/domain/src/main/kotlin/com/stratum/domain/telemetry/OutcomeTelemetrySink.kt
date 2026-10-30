package com.stratum.domain.telemetry

/**
 * Network boundary for the countable outcome stream (STR-242).
 *
 * Implemented in `:core:inference` on the authenticated collector transport
 * (the tier-2 `ingestClientLogs` callable shape: Auth + App Check enforced,
 * no Grafana/Loki write token in the APK). Faked in tests.
 */
interface OutcomeTelemetrySink {
    fun record(outcome: OutcomeTelemetry)
}

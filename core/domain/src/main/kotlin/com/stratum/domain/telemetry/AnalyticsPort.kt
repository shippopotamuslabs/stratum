package com.stratum.domain.telemetry

/**
 * Domain port for product analytics events (Firebase Analytics).
 *
 * Separated from [com.stratum.domain.logging.LoggingPort] (operational logs) and
 * [CrashTelemetryPort] (crash attribution) so event instrumentation can evolve independently.
 *
 * Firebase limits shape this contract: at most 500 distinct event types per app and short
 * names/values (event names keep to 40 characters, values to 100). Prefer a small set of
 * stable event types with parameters (e.g. one `flag_exposure` type keyed by flag) over
 * per-entity event names. Keys and values are scrubbed for PII by platform adapters before
 * transmission; implementations must never throw.
 */
interface AnalyticsPort {
    fun logEvent(
        name: String,
        params: Map<String, String> = emptyMap(),
    )
}

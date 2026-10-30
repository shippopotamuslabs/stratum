package com.stratum.domain.telemetry

import com.stratum.domain.logging.LoggingPort

/**
 * Severity tiers for queryable client logs (STR-27 tier 2).
 *
 * Only [WARNING] (expected failures, validation rejections) and [ERROR]
 * ([LoggingPort.errorSkippingNonFatal][com.stratum.domain.logging.LoggingPort.errorSkippingNonFatal])
 * are uploaded to the ingestion endpoint. Unexpected failures stay on
 * [LoggingPort.error][com.stratum.domain.logging.LoggingPort.error] and are Crashlytics-only.
 *
 * [OUTCOME] carries the STR-242 countable outcome series (successes and expected
 * failures as structured facts, not log lines). The tier-2 pipeline is reused
 * as the authenticated collector transport for outcomes; WARNING/ERROR entry
 * production is retired in slice 4.
 */
enum class ClientLogSeverity {
    WARNING,
    ERROR,
    OUTCOME,
}

/**
 * One structured client log entry bound for the ingestion endpoint.
 *
 * No stack traces: tier 2 carries expected outcomes where the exception type
 * (folded into [message] by the forwarding adapter) plus [tag] is enough to
 * group via SQL. PII must already be scrubbed by the caller.
 *
 * [outcome]/[reasonCode] carry the STR-242 countable outcome vocabulary as flat
 * columns (Grafana groups by them); they are null for legacy WARNING/ERROR
 * entries. Values come from the emitter's closed vocabulary — free-text detail
 * stays in [message] so series cardinality stays bounded.
 */
data class ClientLogEntry(
    val severity: ClientLogSeverity,
    val tag: String,
    val message: String,
    val clientTimestampMs: Long,
    val sessionId: String?,
    val durationMs: Long? = null,
    val outcome: String? = null,
    val reasonCode: String? = null,
) {
    init {
        // The flat outcome columns exist only on the countable OUTCOME series;
        // legacy WARNING/ERROR entries must not carry them, and an OUTCOME
        // entry without an outcome class would be ungroupable in SQL.
        require((outcome != null) == (severity == ClientLogSeverity.OUTCOME)) {
            "outcome must be present iff severity is OUTCOME (severity=$severity, outcome=$outcome)"
        }
        require(severity == ClientLogSeverity.OUTCOME || reasonCode == null) {
            "reasonCode must be null unless severity is OUTCOME (severity=$severity)"
        }
    }
}

/**
 * Upload envelope: the entries plus client-side accounting the server cannot
 * observe ([droppedEntryCount]) and environment fields kept out of entries so
 * every BigQuery row stays small.
 */
data class ClientLogBatch(
    val entries: List<ClientLogEntry>,
    val droppedEntryCount: Int,
    val appVersion: String,
    val platform: String = PLATFORM_ANDROID,
) {
    companion object {
        const val PLATFORM_ANDROID = "android"
        const val APP_VERSION_UNKNOWN = "unknown"
    }
}

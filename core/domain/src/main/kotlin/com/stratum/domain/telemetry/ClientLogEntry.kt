package com.stratum.domain.telemetry

/**
 * Severity tiers for queryable client logs (STR-27 tier 2).
 *
 * Only [WARNING] (expected failures, validation rejections) and [ERROR]
 * ([LoggingPort.errorSkippingNonFatal][com.stratum.domain.logging.LoggingPort.errorSkippingNonFatal])
 * are uploaded to the ingestion endpoint. Unexpected failures stay on
 * [LoggingPort.error][com.stratum.domain.logging.LoggingPort.error] and are Crashlytics-only.
 */
enum class ClientLogSeverity {
    WARNING,
    ERROR,
}

/**
 * One structured client log entry bound for the ingestion endpoint.
 *
 * No stack traces: tier 2 carries expected outcomes where the exception type
 * (folded into [message] by the forwarding adapter) plus [tag] is enough to
 * group via SQL. PII must already be scrubbed by the caller.
 */
data class ClientLogEntry(
    val severity: ClientLogSeverity,
    val tag: String,
    val message: String,
    val clientTimestampMs: Long,
    val sessionId: String?,
    val durationMs: Long? = null,
)

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

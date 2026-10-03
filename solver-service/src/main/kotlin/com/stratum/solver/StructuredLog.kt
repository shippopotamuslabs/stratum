package com.stratum.solver

import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString

/**
 * Structured logging conventions for the Cloud Run solver (STR-28).
 * Mirrors `functions/src/observability/structuredLog.ts` so both server
 * surfaces emit the same queryable shape:
 *
 * - `severity`: Cloud Logging severity; the routing sink reads this field.
 * - `message`: human-readable summary (no PII beyond uid/jobId correlation).
 * - `service`: emitting service (`stratum-solver` here).
 * - `uid` / `jobId`: top-level correlation fields — never interpolated only
 *   into `message` — so BigQuery can filter `jsonPayload.jobId` without regex.
 * - `durationMs`: step timing (solve, dispatch) as a structured number.
 *
 * Error Reporting: only [StructuredLogger.reportError] opens an issue. It adds
 * the `ReportedErrorEvent` `@type` marker plus `serviceContext`, writes to
 * stderr, and embeds the exception type + stack trace in `message` (grouping
 * fingerprints the stack). `error()` without a Throwable stays queryable but
 * does not open an issue — use it for config errors and expected rejections.
 */
enum class LogSeverity(val value: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    NOTICE("NOTICE"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    CRITICAL("CRITICAL"),
}

/** JSON-encoded log line builders. Pure — safe to unit test. */
object StructuredLog {
    /** Error Reporting marker: its presence ingests the entry as an issue. */
    const val ERROR_REPORT_TYPE =
        "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent"

    /**
     * Build a log line. [extra] values must already be JSON-encoded fragments
     * (use [jsonString] / primitives' `toString()`), appended after the
     * required fields.
     */
    fun record(
        severity: LogSeverity,
        service: String,
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, String>> = emptyList(),
    ): String {
        val fields = mutableListOf(
            "severity" to jsonString(severity.value),
            "message" to jsonString(message),
            "service" to jsonString(service),
        )
        if (uid != null) fields += "uid" to jsonString(uid)
        if (jobId != null) fields += "jobId" to jsonString(jobId)
        if (durationMs != null) fields += "durationMs" to durationMs.toString()
        fields += extra
        return jsonObject(fields)
    }

    /** Build an Error Reporting line for [error]. Always ERROR + stderr. */
    fun errorReport(
        service: String,
        message: String,
        error: Throwable,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
    ): String {
        val type = error::class.java.name
        val errorMessage = error.message.orEmpty()
        val stack = error.stackTraceToString()
        val fields = mutableListOf(
            "@type" to jsonString(ERROR_REPORT_TYPE),
            "severity" to jsonString(LogSeverity.ERROR.value),
            "message" to jsonString("$message: $type: $errorMessage\n$stack"),
            "serviceContext" to jsonObject(listOf("service" to jsonString(service))),
            "service" to jsonString(service),
        )
        if (uid != null) fields += "uid" to jsonString(uid)
        if (jobId != null) fields += "jobId" to jsonString(jobId)
        if (durationMs != null) fields += "durationMs" to durationMs.toString()
        fields += "errorType" to jsonString(type)
        fields += "errorMessage" to jsonString(errorMessage)
        fields += "stackTrace" to jsonString(stack)
        return jsonObject(fields)
    }
}

fun interface LogSink {
    fun write(line: String, toStderr: Boolean)
}

/**
 * Emitting logger. Inject [sink] in tests to capture lines; the default writes
 * single-line JSON to stdout (below ERROR) or stderr (ERROR and above), where
 * the Cloud Logging agent picks up the `severity` field.
 */
class StructuredLogger(
    private val service: String,
    private val sink: LogSink = LogSink { line, toStderr ->
        if (toStderr) System.err.println(line) else println(line)
    },
) {
    fun info(
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, String>> = emptyList(),
    ) {
        sink.write(
            StructuredLog.record(LogSeverity.INFO, service, message, uid, jobId, durationMs, extra),
            false,
        )
    }

    fun warn(
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, String>> = emptyList(),
    ) {
        sink.write(
            StructuredLog.record(
                LogSeverity.WARNING,
                service,
                message,
                uid,
                jobId,
                durationMs,
                extra,
            ),
            true,
        )
    }

    /** ERROR without an exception: queryable, but opens no Error Reporting issue. */
    fun error(
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, String>> = emptyList(),
    ) {
        sink.write(
            StructuredLog.record(LogSeverity.ERROR, service, message, uid, jobId, durationMs, extra),
            true,
        )
    }

    /** Unexpected failure WITH a Throwable: Error Reporting issue on stderr. */
    fun reportError(
        message: String,
        error: Throwable,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
    ) {
        sink.write(StructuredLog.errorReport(service, message, error, uid, jobId, durationMs), true)
    }
}

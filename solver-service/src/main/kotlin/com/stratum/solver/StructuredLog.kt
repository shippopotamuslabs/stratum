package com.stratum.solver

import com.stratum.domain.json.jsonNumber
import com.stratum.domain.json.jsonObject
import com.stratum.domain.json.jsonString
import com.stratum.domain.json.jsonValue

/**
 * Structured logging conventions for the Cloud Run solver (STR-28).
 * Mirrors `functions/src/observability/structuredLog.ts` so both server
 * surfaces emit the same queryable shape:
 *
 * - `severity`: Cloud Logging severity; the routing sink reads this field.
 * - `message`: human-readable summary (no PII beyond uid/jobId correlation).
 * - `service`: emitting service ([SERVICE_NAME] here).
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
     * Cloud Run service name shared by the solver, the synthesis dispatcher
     * handler, and the application bootstrap: the Error Reporting
     * `serviceContext` and the top-level `service` field must agree, so the
     * constant lives with the logging conventions, not on one worker class.
     */
    const val SERVICE_NAME = "stratum-solver"

    /**
     * Build a log line. [extra] values are typed and encoded centrally via
     * [jsonValue] — callers pass raw values (String, Number, Boolean, null),
     * never pre-encoded fragments, so a missed `jsonString()` can no longer
     * emit invalid JSON or a quoted number.
     */
    fun record(
        severity: LogSeverity,
        service: String,
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, Any?>> = emptyList(),
    ): String {
        val fields = mutableListOf(
            "severity" to jsonString(severity.value),
            "message" to jsonString(message),
            "service" to jsonString(service),
        )
        fields.addCorrelation(uid, jobId, durationMs)
        fields += extra.map { (key, value) -> key to jsonValue(value) }
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
        extra: List<Pair<String, Any?>> = emptyList(),
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
        fields.addCorrelation(uid, jobId, durationMs)
        fields += "errorType" to jsonString(type)
        fields += "errorMessage" to jsonString(errorMessage)
        fields += "stackTrace" to jsonString(stack)
        fields += extra.map { (key, value) -> key to jsonValue(value) }
        return jsonObject(fields)
    }

    /**
     * Shared uid/jobId/durationMs assembly so [record] and [errorReport]
     * cannot drift apart.
     */
    private fun MutableList<Pair<String, String>>.addCorrelation(
        uid: String?,
        jobId: String?,
        durationMs: Long?,
    ) {
        if (uid != null) this += "uid" to jsonString(uid)
        if (jobId != null) this += "jobId" to jsonString(jobId)
        if (durationMs != null) this += "durationMs" to jsonNumber(durationMs)
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
        extra: List<Pair<String, Any?>> = emptyList(),
    ) = emit(LogSeverity.INFO, message, uid, jobId, durationMs, extra, toStderr = false)

    fun warn(
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, Any?>> = emptyList(),
    ) = emit(LogSeverity.WARNING, message, uid, jobId, durationMs, extra, toStderr = true)

    /** ERROR without an exception: queryable, but opens no Error Reporting issue. */
    fun error(
        message: String,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, Any?>> = emptyList(),
    ) = emit(LogSeverity.ERROR, message, uid, jobId, durationMs, extra, toStderr = true)

    /** Unexpected failure WITH a Throwable: Error Reporting issue on stderr. */
    fun reportError(
        message: String,
        error: Throwable,
        uid: String? = null,
        jobId: String? = null,
        durationMs: Long? = null,
        extra: List<Pair<String, Any?>> = emptyList(),
    ) {
        sink.write(
            StructuredLog.errorReport(service, message, error, uid, jobId, durationMs, extra),
            true,
        )
    }

    /** Single emission path: every public method delegates here. */
    private fun emit(
        severity: LogSeverity,
        message: String,
        uid: String?,
        jobId: String?,
        durationMs: Long?,
        extra: List<Pair<String, Any?>>,
        toStderr: Boolean,
    ) {
        sink.write(
            StructuredLog.record(severity, service, message, uid, jobId, durationMs, extra),
            toStderr,
        )
    }
}

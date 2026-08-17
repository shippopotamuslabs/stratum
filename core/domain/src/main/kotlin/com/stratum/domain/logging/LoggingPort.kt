package com.stratum.domain.logging

/**
 * Domain port for structured logging. Platform adapters (Android Log, Crashlytics, and a future
 * OpenTelemetry sink) live outside `:core:domain` so use cases and ports can log without depending
 * on the Android SDK. Inject a single [LoggingPort] at boundaries; adapters may fan out to
 * multiple sinks. OpenTelemetry must never embed Grafana/Loki write tokens in the APK — export
 * only through an authenticated Firebase Auth / App Check collector.
 */
interface LoggingPort {
    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warning(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)

    /**
     * Logs [message] and [throwable] without recording a non-fatal crash report.
     * Use immediately before a process-level fatal handler that will report [throwable].
     */
    fun errorSkippingNonFatal(tag: String, message: String, throwable: Throwable) {
        error(tag, message, throwable)
    }
}

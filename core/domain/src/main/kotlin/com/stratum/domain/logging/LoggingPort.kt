package com.stratum.domain.logging

/**
 * Domain port for structured logging. Platform adapters (Android Log, Crashlytics, OpenTelemetry,
 * etc.) live outside `:core:domain` so use cases and ports can log without depending on the
 * Android SDK. Inject a single [LoggingPort] at boundaries; adapters may fan out to multiple sinks.
 */
interface LoggingPort {
    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warning(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

package com.stratum.domain.logging

import com.stratum.domain.telemetry.TelemetryPiiScrubber
import com.stratum.domain.telemetry.TelemetryThrowableSanitizer
import com.stratum.domain.telemetry.TelemetryRouter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Last-resort failure reporting so unexpected crashes always leave a stack via [LoggingPort]
 * (Logcat + Crashlytics breadcrumbs through the platform adapter).
 *
 * Install once at process start. Chains to the previous uncaught handler so the system crash
 * dialog and Firebase Crashlytics fatal path still run.
 */
@Singleton
class ProcessErrorSafetyNet @Inject constructor(
    private val loggingPort: LoggingPort,
    private val telemetryRouter: TelemetryRouter,
) {
    /**
     * Logs the throwable without a Crashlytics non-fatal, then delegates to the previous default
     * handler with a PII-sanitized copy. Safe to call once from [android.app.Application.onCreate].
     */
    fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sanitized = TelemetryThrowableSanitizer.sanitize(throwable)
            try {
                val exceptionName = throwable::class.simpleName ?: "Throwable"
                val message = TelemetryPiiScrubber.scrub(throwable.message) ?: "no message"
                loggingPort.errorSkippingNonFatal(
                    TAG_UNCAUGHT,
                    "Uncaught $exceptionName in thread ${thread.name}: $message",
                    sanitized,
                )
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, sanitized)
                } else {
                    exitProcess(FATAL_EXIT_CODE)
                }
            }
        }
    }

    /**
     * Handler for process-lifetime scopes ([com.stratum.domain.di.ApplicationScope]).
     * Does not rethrow — a SupervisorJob parent keeps siblings alive; the failure is recorded.
     *
     * Terminal boundary: records through [TelemetryRouter] instead of [LoggingPort]
     * so the exceptional fact has exactly one record (non-fatal + bare counter).
     */
    fun applicationCoroutineExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            telemetryRouter.recordExceptional(TAG_APPLICATION_SCOPE, throwable)
        }

    private companion object {
        const val TAG_UNCAUGHT = "Uncaught"
        const val TAG_APPLICATION_SCOPE = "ApplicationScope"
        const val FATAL_EXIT_CODE = 10
    }
}

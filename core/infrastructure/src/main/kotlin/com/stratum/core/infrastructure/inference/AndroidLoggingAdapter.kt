package com.stratum.core.infrastructure.inference

import android.util.Log
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.CrashTelemetryPort
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import com.stratum.domain.telemetry.TelemetryThrowableSanitizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LoggingPort] backed by `android.util.Log` with warning/error breadcrumbs and non-fatal
 * delegation to [CrashTelemetryPort]. Messages are scrubbed before Logcat and Crashlytics.
 */
@Singleton
class AndroidLoggingAdapter @Inject constructor(
    private val crashTelemetryPort: CrashTelemetryPort,
) : LoggingPort {
    override fun debug(tag: String, message: String) {
        Log.d(tag, scrubbed(message))
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, scrubbed(message))
    }

    override fun warning(tag: String, message: String) {
        val scrubbedMessage = scrubbed(message)
        Log.w(tag, scrubbedMessage)
        crashTelemetryPort.recordBreadcrumb("$tag: $scrubbedMessage")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        val scrubbedMessage = scrubbed(message)
        val sanitized = throwable?.let(TelemetryThrowableSanitizer::sanitize)
        if (sanitized != null) {
            Log.e(tag, scrubbedMessage, sanitized)
        } else {
            Log.e(tag, scrubbedMessage)
        }
        crashTelemetryPort.recordBreadcrumb("$tag: $scrubbedMessage")
        if (throwable != null) {
            crashTelemetryPort.recordNonFatal(throwable)
        }
    }

    override fun errorSkippingNonFatal(tag: String, message: String, throwable: Throwable) {
        val scrubbedMessage = scrubbed(message)
        val sanitized = TelemetryThrowableSanitizer.sanitize(throwable)
        Log.e(tag, scrubbedMessage, sanitized)
        crashTelemetryPort.recordBreadcrumb("$tag: $scrubbedMessage")
    }

    private fun scrubbed(message: String): String =
        TelemetryPiiScrubber.scrub(message).orEmpty()
}

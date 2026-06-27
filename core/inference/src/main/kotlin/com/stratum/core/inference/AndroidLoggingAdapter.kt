package com.stratum.core.inference

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LoggingPort] backed by `android.util.Log` with non-fatal reporting to Firebase Crashlytics on
 * [error] calls that include a [Throwable]. Future OpenTelemetry export can compose alongside this
 * adapter (same [LoggingPort] seam — see pocket-crew's telemetry wiring).
 */
@Singleton
class AndroidLoggingAdapter @Inject constructor() : LoggingPort {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warning(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        if (throwable == null) {
            return
        }
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            val scrubbedMessage = TelemetryPiiScrubber.scrub(message).orEmpty()
            crashlytics.log("$tag: $scrubbedMessage")
            crashlytics.recordException(throwable)
        }.onFailure { crashlyticsError ->
            Log.e(
                TAG,
                "Crashlytics not available. Error: $message",
                crashlyticsError,
            )
        }
    }

    private companion object {
        const val TAG = "AndroidLoggingAdapter"
    }
}

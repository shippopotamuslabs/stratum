package com.stratum.core.inference

import android.util.Log
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.CrashTelemetryPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LoggingPort] backed by `android.util.Log` with non-fatal and breadcrumb delegation to [CrashTelemetryPort].
 */
@Singleton
class AndroidLoggingAdapter @Inject constructor(
    private val crashTelemetryPort: CrashTelemetryPort,
) : LoggingPort {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
        crashTelemetryPort.recordBreadcrumb("$tag: $message")
    }

    override fun warning(tag: String, message: String) {
        Log.w(tag, message)
        crashTelemetryPort.recordBreadcrumb("$tag: $message")
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        crashTelemetryPort.recordBreadcrumb("$tag: $message")
        if (throwable != null) {
            crashTelemetryPort.recordNonFatal(throwable)
        }
    }

    override fun errorSkippingNonFatal(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
        crashTelemetryPort.recordBreadcrumb("$tag: $message")
    }
}

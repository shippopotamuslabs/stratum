package com.stratum.core.inference

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.telemetry.ClientLogBuffer
import com.stratum.domain.telemetry.ClientLogSeverity
import com.stratum.domain.telemetry.TelemetryPiiScrubber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LoggingPort] that keeps Crashlytics behavior (via [AndroidLoggingAdapter])
 * and additionally forwards tier-2 signals to the queryable pipeline (STR-27):
 * [warning] and [errorSkippingNonFatal].
 *
 * [error] is deliberately NOT forwarded — unexpected failures are Tier 1
 * (Crashlytics-only) so tier 2 never alerts and tier 1 keeps its signal.
 */
@Singleton
class ForwardingLoggingAdapter @Inject constructor(
    private val delegate: AndroidLoggingAdapter,
    private val buffer: ClientLogBuffer,
) : LoggingPort {
    override fun debug(tag: String, message: String) {
        delegate.debug(tag, message)
    }

    override fun info(tag: String, message: String) {
        delegate.info(tag, message)
    }

    override fun warning(tag: String, message: String) {
        delegate.warning(tag, message)
        buffer.record(ClientLogSeverity.WARNING, tag, scrubbed(message))
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        delegate.error(tag, message, throwable)
    }

    override fun errorSkippingNonFatal(tag: String, message: String, throwable: Throwable) {
        delegate.errorSkippingNonFatal(tag, message, throwable)
        buffer.record(
            ClientLogSeverity.ERROR,
            tag,
            scrubbed("$message | ${throwable.javaClass.name}: ${throwable.message}"),
        )
    }

    private fun scrubbed(message: String): String =
        TelemetryPiiScrubber.scrub(message).orEmpty()
}

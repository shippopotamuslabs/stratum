package com.stratum.feature.onboarding

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject

/**
 * Presentation boundary component (STR-237): drains [OperationalDiagnostic]s
 * carried on sealed outcome branches into [LoggingPort] records, exactly once
 * per diagnostic.
 *
 * ViewModels in this module inject this drain, never a port
 * (ForbiddenPortInViewModel stands). [OperationalDiagnostic.Level.ERROR]
 * records through `LoggingPort.error` with the cause attached;
 * [OperationalDiagnostic.Level.WARNING] records through
 * `LoggingPort.warning`, which carries no throwable.
 *
 * [recordError] is the direct-record path for boundary failures that
 * originate outside a use case (the retired error-use-case route): the
 * record keeps its tag/message exactly, and the throwable stays nullable -
 * a record whose cause is null by design is never given a fabricated one.
 */
class DiagnosticDrain @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    fun drain(diagnostics: List<OperationalDiagnostic>) {
        diagnostics.forEach { drain(it) }
    }

    fun recordError(tag: String, message: String, throwable: Throwable? = null) {
        loggingPort.error(tag, message, throwable)
    }

    fun drain(diagnostic: OperationalDiagnostic?) {
        if (diagnostic == null) return
        when (diagnostic.level) {
            OperationalDiagnostic.Level.ERROR ->
                loggingPort.error(diagnostic.tag, diagnostic.message, diagnostic.cause)
            OperationalDiagnostic.Level.WARNING ->
                loggingPort.warning(diagnostic.tag, diagnostic.message)
        }
    }
}

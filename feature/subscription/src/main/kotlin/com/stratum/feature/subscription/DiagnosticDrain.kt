package com.stratum.feature.subscription

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
 */
class DiagnosticDrain @Inject constructor(
    private val loggingPort: LoggingPort,
) {
    fun drain(diagnostics: List<OperationalDiagnostic>) {
        diagnostics.forEach { drain(it) }
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

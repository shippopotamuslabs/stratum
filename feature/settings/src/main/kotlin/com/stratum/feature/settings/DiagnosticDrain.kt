package com.stratum.feature.settings

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.sync.CloudSyncDiagnostic
import com.stratum.application.sync.CloudSyncOperation
import com.stratum.application.sync.toOperationalDiagnostics
import com.stratum.core.infrastructure.logging.LoggingPort
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

    /**
     * Sync entry point: drains a sync operation's [CloudSyncDiagnostic]s
     * exactly once each, rendered through the shared application-layer
     * factory for [operation] (the adapter's legacy tag/message shape, real
     * cause attached) — boundaries never hand-roll that conversion.
     */
    fun drainSync(
        diagnostics: List<CloudSyncDiagnostic>,
        operation: CloudSyncOperation,
    ) {
        drain(diagnostics.toOperationalDiagnostics(operation))
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

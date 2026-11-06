package com.stratum.application.diagnostics

/**
 * An operational failure recorded as data (STR-237, §10 diagnostics channel).
 *
 * A use case whose sealed outcome branch previously lost the operational
 * failure behind a `LoggingPort` call instead carries the failure on the
 * branch as an [OperationalDiagnostic]: the legacy [tag] and rendered
 * [message], plus the real [cause]. The value is constructed below the
 * boundary and is never logged below the boundary; a presentation boundary
 * component drains it exactly once through its module's `DiagnosticDrain`,
 * which makes the single `LoggingPort` record.
 *
 * Precedents: [com.stratum.application.lift.MergeLiftsOutcome] carries its
 * §10 diagnostics on the success payload, drained at the feature boundary,
 * and the C9 shape carries diagnostics on a failure branch —
 * `ProgramTemplateImportOutcome.PersistFailed` carries its rollback
 * diagnostics the same way. Diagnostics never reach UI state.
 */
data class OperationalDiagnostic(
    val tag: String,
    val message: String,
    val cause: Throwable,
    val level: Level = Level.ERROR,
) {
    enum class Level {
        ERROR,
        WARNING,
    }
}

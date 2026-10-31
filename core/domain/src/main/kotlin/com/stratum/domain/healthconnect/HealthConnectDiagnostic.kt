package com.stratum.domain.healthconnect

/**
 * A non-fatal Health Connect metrics degradation recorded while loading dashboard pages
 * (STR-257).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs (CODE_STYLE_RULES.md §1),
 * so the metrics load records them on the returned [HealthMetricsPages] and the dashboard
 * boundary reports each one exactly once through its error-logging wiring.
 * Diagnostics never reach the UI.
 */
sealed interface HealthConnectDiagnostic {
    /**
     * The snapshot read failed after grants were resolved, so the pages were assembled
     * from an empty snapshot — indistinguishable from "no metrics" without this record.
     */
    data class SnapshotReadFailed(
        val cause: Throwable,
    ) : HealthConnectDiagnostic
}

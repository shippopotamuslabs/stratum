package com.stratum.domain.healthconnect

/**
 * A non-fatal Health Connect metrics degradation recorded while loading dashboard pages
 * (STR-257).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs (CODE_STYLE_RULES.md §1),
 * so the metrics load records them on the returned [HealthMetricsPages] and the dashboard
 * boundary reports each one exactly once through its error-logging wiring.
 * Diagnostics never reach the UI.
 *
 * The failure kind is recorded here; the caught `Throwable` is logged once where it is
 * caught (in the metrics use case) and never stored on the diagnostic.
 */
sealed interface HealthConnectDiagnostic {
    /**
     * The snapshot read failed after grants were resolved, so the pages were assembled
     * from an empty snapshot — indistinguishable from "no metrics" without this record.
     * The read-side failure is unclassified, so the kind is always [HealthFailureKind.Unknown].
     */
    data class SnapshotReadFailed(
        val kind: HealthFailureKind,
    ) : HealthConnectDiagnostic
}

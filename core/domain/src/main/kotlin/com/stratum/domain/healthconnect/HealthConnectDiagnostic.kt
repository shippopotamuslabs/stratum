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
 * The failure kind is recorded here. For [SnapshotReadFailed] the caught `Throwable`
 * also rides the diagnostic as a §10 diagnostic value (Sean's AQ1 ruling, 2026-11-06,
 * amending the earlier never-stored rule for this variant): the metrics use case
 * records nothing, and the cause is recorded exactly once at the dashboard boundary
 * drain.
 */
sealed interface HealthConnectDiagnostic {
    /**
     * The snapshot read failed after grants were resolved, so the load returns explicit
     * degraded pages carrying this diagnostic (STR-240) — the dashboard renders them as
     * unavailable, never as assembled "no metrics" pages.
     * The read-side failure is unclassified, so the kind is always [HealthFailureKind.Unknown].
     * [cause] is the real caught throwable, carried as a §10 diagnostic value and
     * recorded exactly once at the dashboard boundary drain (AQ1 ruling, 2026-11-06).
     */
    data class SnapshotReadFailed(
        val kind: HealthFailureKind,
        val cause: Throwable,
    ) : HealthConnectDiagnostic
}

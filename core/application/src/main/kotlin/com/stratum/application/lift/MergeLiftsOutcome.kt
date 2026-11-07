package com.stratum.application.lift

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.lift.identity.LiftId

/**
 * Success payload of [MergeLiftsUseCase]: the merge itself reports
 * success/failure through [MergeLiftsResult], while degradations that the
 * merge tolerates ride along as [diagnostics].
 *
 * Placement note (CODE-STYLE-GOSPEL §11): this type is owned by the application
 * layer, not the domain. The degradation it records is detected in use-case
 * coordination — a post-commit derived-data recompute failing after the structural
 * merge already succeeded — not in domain math, so the domain stays free of this
 * operational concern. Diagnostics are drained exactly once at the feature
 * boundary and never reach UI state.
 */
data class MergeLiftsOutcome(
    val diagnostics: List<MergeLiftsDiagnostic> = emptyList(),
    /**
     * STR-237: operational records of tolerated degradations detected in
     * use-case coordination (the post-merge derived-data rebuild), carried
     * in the standard carrier and drained by the boundary's DiagnosticDrain
     * alongside [diagnostics].
     */
    val operationalDiagnostics: List<OperationalDiagnostic> = emptyList(),
)

/**
 * A non-fatal merge degradation recorded as data (STR-255).
 *
 * Diagnostics carry what was skipped and why — the identifying context plus the
 * failure as data — and are constructed, never logged, below the boundary.
 */
sealed interface MergeLiftsDiagnostic {
    /**
     * The post-commit personal-records recompute failed after the structural merge
     * succeeded, so PR flags on the survivor's history may be stale. The merge
     * itself is unaffected.
     */
    data class PersonalRecordsNotRecomputed(
        val targetLiftId: LiftId,
        val cause: Throwable,
    ) : MergeLiftsDiagnostic
}

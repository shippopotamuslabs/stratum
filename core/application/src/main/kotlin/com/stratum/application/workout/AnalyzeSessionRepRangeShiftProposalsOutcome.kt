package com.stratum.application.workout

import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.RepRangeShiftProposal

/**
 * Outcome of [AnalyzeSessionRepRangeShiftProposalsUseCase]: the analysis itself
 * never fails the caller — a failure degrades to empty proposals and rides
 * along as [diagnostics].
 *
 * Placement note (CODE-STYLE-GOSPEL §11): this type is owned by the application
 * layer, not the domain. The degradation it records is detected in use-case
 * coordination — a repository/flow read failing while analyzing a session —
 * not in domain math, so the domain stays free of this operational concern.
 * Diagnostics are constructed, never logged, below the boundary; they are
 * drained exactly once at the feature boundary and never reach UI state.
 */
data class AnalyzeSessionRepRangeShiftProposalsOutcome(
    val proposals: List<RepRangeShiftProposal> = emptyList(),
    val diagnostics: List<RepRangeShiftDiagnostic> = emptyList(),
)

/**
 * A non-fatal rep-range-shift analysis degradation recorded as data (STR-259).
 *
 * Diagnostics carry what was degraded and why — the identifying context plus the
 * failure as data — and are constructed, never logged, below the boundary.
 * Coroutine cancellation is not a diagnostic: it propagates.
 */
sealed interface RepRangeShiftDiagnostic {
    /**
     * The session analysis failed, so the caller shows no proposals. An empty
     * analysis from disabled preferences or an ineligible session is not a
     * failure and carries no diagnostic.
     */
    data class AnalysisFailed(
        val sessionId: TrainingSessionId,
        val cause: Throwable,
    ) : RepRangeShiftDiagnostic
}

package com.stratum.application.lift

import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.identity.TrainingSessionId

/**
 * Outcome of [AnalyzeSessionEquipmentProposalsUseCase]: the analysis itself
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
data class AnalyzeSessionEquipmentProposalsOutcome(
    val proposals: List<EquipmentIncrementProposal> = emptyList(),
    val baselineMicroByProposalId: Map<String, List<Weight>> = emptyMap(),
    val diagnostics: List<EquipmentProposalDiagnostic> = emptyList(),
)

/**
 * A non-fatal equipment-analysis degradation recorded as data (STR-259).
 *
 * Diagnostics carry what was degraded and why — the identifying context plus the
 * failure as data — and are constructed, never logged, below the boundary.
 * Coroutine cancellation is not a diagnostic: it propagates.
 */
sealed interface EquipmentProposalDiagnostic {
    /**
     * The session analysis failed, so the caller shows no proposals. An empty
     * analysis from disabled preferences or a session with no anomalies is not
     * a failure and carries no diagnostic.
     */
    data class AnalysisFailed(
        val sessionId: TrainingSessionId,
        val cause: Throwable,
    ) : EquipmentProposalDiagnostic
}

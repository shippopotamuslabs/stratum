package com.stratum.domain.training

/**
 * Diagnostic value for an unbounded operational failure while starting a
 * training session (diagnostics as values, CODE_STYLE_RULES §10 precedent).
 * [StartTrainingSessionResult] is domain-owned, so its carrier is
 * domain-owned too — it cannot be the application layer's
 * `OperationalDiagnostic`. It carries the REAL throwable as [cause]; the
 * outcome consumer records it exactly once at its boundary, rendering the
 * legacy tag and message there. Diagnostics never reach UI state.
 */
data class TrainingSessionFailureDiagnostic(
    val cause: Throwable,
)

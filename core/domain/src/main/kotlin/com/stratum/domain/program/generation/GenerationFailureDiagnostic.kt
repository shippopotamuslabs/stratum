package com.stratum.domain.program.generation

/**
 * Diagnostic value for an unbounded operational failure inside program
 * generation (diagnostics as values, CODE_STYLE_RULES §10 precedent).
 * [GenerateProgramResult] is domain-owned, so its carrier is domain-owned
 * too — it cannot be the application layer's `OperationalDiagnostic`. It
 * carries the REAL throwable as [cause]; the outcome consumer records it
 * exactly once at its boundary (CreateProgramGenerateController via
 * `TelemetryRouter.recordExceptional`, OnboardingViewModel via its recorder
 * path). Diagnostics never reach UI state.
 */
data class GenerationFailureDiagnostic(
    val cause: Throwable,
)

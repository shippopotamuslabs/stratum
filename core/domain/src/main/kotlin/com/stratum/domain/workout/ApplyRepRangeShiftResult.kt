package com.stratum.domain.workout

sealed interface ApplyRepRangeShiftResult {
    /**
     * The shift was applied. A tolerated post-save failure (the audit event
     * append) rides [diagnostics] as [RepRangeShiftFailureDiagnostic]s for
     * the boundary to record exactly once.
     */
    data class Success(
        val diagnostics: List<RepRangeShiftFailureDiagnostic> = emptyList(),
    ) : ApplyRepRangeShiftResult
    data object ProgramNotFound : ApplyRepRangeShiftResult
    data object LiftNotFound : ApplyRepRangeShiftResult
    data class ProposalMismatch(val reason: String) : ApplyRepRangeShiftResult
    /**
     * The shift could not be applied because a repository read or write failed.
     * The originating throwable rides this branch as a
     * [RepRangeShiftFailureDiagnostic] (cause only — STR-237 Route (i)); the
     * boundary renders the legacy tag + message and records it exactly once.
     * Coroutine cancellation is not this branch: it propagates as cancellation.
     */
    data class PersistenceFailed(
        val diagnostics: List<RepRangeShiftFailureDiagnostic>,
    ) : ApplyRepRangeShiftResult
}

val ApplyRepRangeShiftResult.isSuccess: Boolean
    get() = this is ApplyRepRangeShiftResult.Success

val ApplyRepRangeShiftResult.isFailure: Boolean
    get() = this !is ApplyRepRangeShiftResult.Success

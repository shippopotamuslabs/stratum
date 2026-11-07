package com.stratum.domain.workout

sealed interface ApplyRepRangeShiftResult {
    data object Success : ApplyRepRangeShiftResult
    data object ProgramNotFound : ApplyRepRangeShiftResult
    data object LiftNotFound : ApplyRepRangeShiftResult
    data class ProposalMismatch(val reason: String) : ApplyRepRangeShiftResult
    /**
     * The shift could not be applied because a repository read or write failed.
     * The originating throwable is logged once at the use-case boundary through the
     * diagnostics channel (LoggingPort); it never crosses as a payload (STR-243).
     * Coroutine cancellation is not this branch: it propagates as cancellation.
     */
    data object PersistenceFailed : ApplyRepRangeShiftResult
}

val ApplyRepRangeShiftResult.isSuccess: Boolean
    get() = this is ApplyRepRangeShiftResult.Success

val ApplyRepRangeShiftResult.isFailure: Boolean
    get() = this !is ApplyRepRangeShiftResult.Success
